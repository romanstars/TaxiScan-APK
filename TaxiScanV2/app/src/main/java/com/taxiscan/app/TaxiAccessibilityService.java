package com.taxiscan.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaxiAccessibilityService extends AccessibilityService {
  private static final int GREEN=0xff38d889, YELLOW=0xffffd600, WHITE=0xfff2f5f3;
  private static final int MUTED=0xffaab5af, CARD=0xff1b211e, LINE=0xff343c38, RED=0xffff6f69;
  private static final Pattern MONEY=Pattern.compile("(?i)(?:₴|грн|uah)\\s*(\\d{1,4}(?:[.,]\\d{1,2})?)|(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(?:₴|грн|uah)");
  private static final Pattern KM=Pattern.compile("(?i)(\\d{1,2}(?:[.,]\\d{1,2})?)\\s*км");
  private WindowManager wm;
  private LinearLayout overlay;
  private ScrollView overlayBody;
  private LinearLayout actions;
  private TextView serviceLabel, compactSummary, resizeButton, fareLabel, rateLabel, tripLabel, fuelLabel, wearLabel, netLabel, verdictLabel;
  private boolean compact;
  private float dragStartX,dragStartY;
  private int dragOriginX,dragOriginY;
  private long lastEventAt;
  private String lastSignature="";

  @Override public void onServiceConnected(){super.onServiceConnected();wm=(WindowManager)getSystemService(WINDOW_SERVICE);}

  @Override public void onAccessibilityEvent(AccessibilityEvent event){
    if(event==null||event.getPackageName()==null)return;
    String service=serviceName(event.getPackageName().toString());
    if(service==null||!serviceEnabled(service))return;
    SharedPreferences p=getSharedPreferences(MainActivity.PREF,MODE_PRIVATE);
    if(p.getBoolean("paused",false))return;
    StringBuilder all=new StringBuilder();
    for(CharSequence item:event.getText())if(item!=null)all.append(item).append(' ');
    AccessibilityNodeInfo root=getRootInActiveWindow();
    if(root!=null)collect(root,all,0);
    String raw=all.toString().replaceAll("\\s+"," ").trim();
    if(raw.length()<5)return;
    double amount=extract(MONEY,raw), pickup=pickup(raw), declaredTotal=totalDistance(raw);
    double distance=tripDistance(raw,pickup,declaredTotal);
    if(amount<=0)return;
    String signature=service+":"+amount+":"+distance+":"+raw.hashCode();
    long now=SystemClock.elapsedRealtime();
    if(signature.equals(lastSignature)&&now-lastEventAt<2500)return;
    lastSignature=signature;lastEventAt=now;

    double rating=rating(raw);
    double commission=p.getFloat("commission",15f);
    double fuelPerKm=p.getFloat("fuel_km",1.8f), wearPerKm=p.getFloat("amort_km",1.2f);
    double totalKm=declaredTotal>0?declaredTotal:distance+pickup, fuel=fuelPerKm*totalKm, wear=wearPerKm*totalKm;
    double net=amount*(1-commission/100.0)-fuel-wear;
    double perKm=totalKm>0?net/totalKm:0;
    double hourly=distance>0?net/(distance/25.0):0;
    boolean good=amount>=p.getInt("min_fare",80)&&perKm>=p.getInt("min_km",10)
      &&hourly>=p.getInt("min_hour",250)&&pickup<=p.getInt("max_pickup",3)
      &&(rating<=0||rating>=p.getInt("min_rating",46)/10.0);

    JSONObject item=new JSONObject();
    try{
      item.put("service",service);item.put("amount",amount);item.put("distance",distance);
      item.put("pickup",pickup);item.put("net",net);item.put("perKm",perKm);item.put("hourly",hourly);
      item.put("fuel",fuel);item.put("wear",wear);item.put("rating",rating);item.put("good",good);
      item.put("time",new SimpleDateFormat("HH:mm:ss",new Locale("uk")).format(new Date()));
      item.put("iso",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).format(new Date()));
    }catch(Exception ignored){}
    JSONArray history;
    try{history=new JSONArray(p.getString("events","[]"));}catch(Exception e){history=new JSONArray();}
    history.put(item);
    while(history.length()>500){JSONArray shorter=new JSONArray();for(int i=1;i<history.length();i++)shorter.put(history.opt(i));history=shorter;}
    p.edit().putString("events",history.toString()).apply();

    if(!p.getBoolean("only_good",true)||good)showOverlay(service,amount,distance,pickup,totalKm,fuel,wear,net,perKm,hourly,good);
    if(p.getBoolean("sound",true))playSignal();
  }

  private String serviceName(String pkg){
    String s=pkg.toLowerCase(Locale.US);
    if(s.contains("bolt")||s.contains("mtakso"))return "Bolt";
    if(s.contains("uklon"))return "Uklon";
    if(s.contains("uber"))return "Uber";
    return null;
  }
  private boolean serviceEnabled(String name){
    String key="service_"+(name.equals("Uber")?"uber":name.toLowerCase(Locale.US));
    return getSharedPreferences(MainActivity.PREF,MODE_PRIVATE).getBoolean(key,!name.equals("Uber"));
  }
  private void collect(AccessibilityNodeInfo node,StringBuilder out,int depth){
    if(node==null||depth>22||out.length()>10000)return;
    CharSequence text=node.getText(),description=node.getContentDescription();
    if(text!=null&&text.length()>0)out.append(text).append(' ');
    if(description!=null&&description.length()>0)out.append(description).append(' ');
    for(int i=0;i<node.getChildCount();i++)collect(node.getChild(i),out,depth+1);
  }
  private double extract(Pattern pattern,String text){
    Matcher m=pattern.matcher(text);if(!m.find())return 0;
    String value=m.group(1)!=null?m.group(1):m.group(2);
    try{return Double.parseDouble(value.replace(',','.'));}catch(Exception e){return 0;}
  }
  private double rating(String text){
    Matcher m=Pattern.compile("(?i)(?:рейтинг|rating)\\D{0,8}(\\d[.,]\\d)").matcher(text);
    if(!m.find())return 0;try{return Double.parseDouble(m.group(1).replace(',','.'));}catch(Exception e){return 0;}
  }
  private double pickup(String text){
    Matcher m=Pattern.compile("(?i)подач[аіу]?\\D{0,20}(\\d{1,2}(?:[.,]\\d{1,2})?)\\s*км").matcher(text);
    if(!m.find())return 0;try{return Double.parseDouble(m.group(1).replace(',','.'));}catch(Exception e){return 0;}
  }
  private double totalDistance(String text){
    Matcher m=Pattern.compile("(?i)(?:загалом|усього|разом|total)\\D{0,14}(\\d{1,2}(?:[.,]\\d{1,2})?)\\s*км").matcher(text);
    if(!m.find())return 0;try{return Double.parseDouble(m.group(1).replace(',','.'));}catch(Exception e){return 0;}
  }
  private double tripDistance(String text,double pickup,double total){
    Matcher labeled=Pattern.compile("(?i)(?:поїздк[аі]|маршрут|trip)\\D{0,24}(\\d{1,2}(?:[.,]\\d{1,2})?)\\s*км").matcher(text);
    if(labeled.find())try{return Double.parseDouble(labeled.group(1).replace(',','.'));}catch(Exception ignored){}
    Matcher m=KM.matcher(text);double first=0,best=0;int count=0;
    while(m.find()){
      double value;try{value=Double.parseDouble(m.group(1).replace(',','.'));}catch(Exception e){continue;}
      if(count++==0)first=value;
      if(pickup>0&&Math.abs(value-pickup)<0.06)continue;
      if(total>0&&value>=total-0.05)continue;
      if(value>best)best=value;
    }
    if(best>0)return best;
    if(first>0&&pickup>0&&Math.abs(first-pickup)<0.06&&total>0)return total-pickup;
    return first;
  }

  private void showOverlay(String service,double amount,double distance,double pickup,double totalKm,
      double fuel,double wear,double net,double perKm,double hourly,boolean good){
    if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this))return;
    if(wm==null)wm=(WindowManager)getSystemService(WINDOW_SERVICE);
    if(overlay==null)createOverlay();
    serviceLabel.setText("●  ЗАМОВЛЕННЯ     •     "+service);
    fareLabel.setText(money(amount));
    rateLabel.setText(String.format(new Locale("uk","UA"),"%.1f ₴/км   •   %.0f ₴/год",perKm,hourly));
    tripLabel.setText(String.format(new Locale("uk","UA"),"Подача  %.1f км     •     Поїздка  %.1f км\nЗагалом  %.1f км",pickup,distance,totalKm));
    fuelLabel.setText(money(fuel));wearLabel.setText(money(wear));netLabel.setText(money(net));
    compactSummary.setText(String.format(new Locale("uk","UA"),"%.0f ₴  •  чистими %.0f ₴",amount,net));
    verdictLabel.setText(good?"✓  Вигідно за вашими фільтрами":"⚠  Не відповідає вашим фільтрам");
    verdictLabel.setTextColor(good?GREEN:YELLOW);
    if(overlay.getWindowToken()==null){try{wm.addView(overlay,params());}catch(Exception ignored){}}
  }

  private void createOverlay(){
    overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);overlay.setPadding(dp(14),dp(14),dp(14),dp(12));
    overlay.setBackground(shape(0xf20c100e,24,0xff315640));
    LinearLayout header=row();serviceLabel=label("",GREEN,15,true);header.addView(serviceLabel,new LinearLayout.LayoutParams(0,-2,1));
    compactSummary=label("",WHITE,13,true);compactSummary.setVisibility(android.view.View.GONE);header.addView(compactSummary);
    resizeButton=actionButton("−",CARD,WHITE);resizeButton.setOnClickListener(v->setCompact(!compact));
    LinearLayout.LayoutParams smallButton=new LinearLayout.LayoutParams(dp(42),dp(42));smallButton.leftMargin=dp(6);header.addView(resizeButton,smallButton);
    TextView closeTop=actionButton("×",CARD,WHITE);closeTop.setOnClickListener(v->hideOverlay());
    header.addView(closeTop,new LinearLayout.LayoutParams(dp(42),dp(42)));
    serviceLabel.setOnTouchListener((view,event)->dragOverlay(event));
    overlay.addView(header);addDivider(overlay);

    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
    LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(3),dp(4),dp(3),dp(4));
    fareLabel=label("",WHITE,52,true);fareLabel.setPadding(0,dp(3),0,0);body.addView(fareLabel);
    rateLabel=label("",MUTED,17,false);body.addView(rateLabel);addDivider(body);
    tripLabel=label("",WHITE,16,false);tripLabel.setLineSpacing(dp(5),1);tripLabel.setPadding(0,dp(4),0,dp(4));body.addView(tripLabel);

    LinearLayout income=new LinearLayout(this);income.setOrientation(LinearLayout.VERTICAL);income.setPadding(dp(13),dp(11),dp(13),dp(11));
    income.setBackground(shape(CARD,16,LINE));TextView incomeTitle=label("▥  РОЗРАХУНОК ДОХОДУ",WHITE,16,true);income.addView(incomeTitle);addDivider(income);
    income.addView(valueRow("⛽  Розхід пального",fuelLabel=label("",WHITE,17,true)));
    income.addView(valueRow("🔧  Амортизація",wearLabel=label("",WHITE,17,true)));
    income.addView(valueRow("₴  Чистий заробіток",netLabel=label("",GREEN,22,true)));
    body.addView(income);verdictLabel=label("",YELLOW,15,true);verdictLabel.setPadding(dp(12),dp(11),dp(12),dp(11));
    verdictLabel.setBackground(shape(0xff292615,12,0xff796b20));LinearLayout.LayoutParams verdictParams=new LinearLayout.LayoutParams(-1,-2);verdictParams.topMargin=dp(10);body.addView(verdictLabel,verdictParams);
    TextView note=label("Розрахунок використовує суму та відстань, які видно на екрані сервісу.",MUTED,12,false);note.setPadding(dp(3),dp(9),dp(3),dp(2));body.addView(note);
    scroll.addView(body);overlayBody=scroll;overlay.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

    actions=row();TextView settings=actionButton("⚙  Налаштування",CARD,WHITE);
    settings.setOnClickListener(v->{hideOverlay();Intent intent=new Intent(this,MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);intent.putExtra("open_settings",true);startActivity(intent);});
    TextView close=actionButton("Закрити",GREEN,Color.BLACK);close.setOnClickListener(v->hideOverlay());
    LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.rightMargin=dp(8);actions.addView(settings,left);
    actions.addView(close,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams actionsParams=new LinearLayout.LayoutParams(-1,-2);actionsParams.topMargin=dp(10);overlay.addView(actions,actionsParams);
  }

  private LinearLayout valueRow(String title,TextView value){
    LinearLayout line=row();TextView name=label(title,MUTED,14,false);line.addView(name,new LinearLayout.LayoutParams(0,-2,1));
    line.addView(value);line.setPadding(0,dp(8),0,dp(5));return line;
  }
  private TextView actionButton(String text,int color,int textColor){
    TextView b=label(text,textColor,16,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(8),dp(13),dp(8),dp(13));b.setBackground(shape(color,14,0x00000000));return b;
  }
  private TextView label(String text,int color,int size,boolean bold){
    TextView v=new TextView(this);v.setText(text);v.setTextColor(color);v.setTextSize(size);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
  }
  private LinearLayout row(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);return row;}
  private void addDivider(LinearLayout parent){android.view.View divider=new android.view.View(this);divider.setBackgroundColor(LINE);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.topMargin=dp(8);p.bottomMargin=dp(8);parent.addView(divider,p);}
  private GradientDrawable shape(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
  private String money(double amount){return String.format(new Locale("uk","UA"),"%.0f ₴",amount);}
  private WindowManager.LayoutParams params(){
    int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
    int screenHeight=getResources().getDisplayMetrics().heightPixels,screenWidth=getResources().getDisplayMetrics().widthPixels;
    SharedPreferences prefs=getSharedPreferences(MainActivity.PREF,MODE_PRIVATE);
    int width=compact?Math.min(dp(360),screenWidth-dp(24)):-1;
    int height=compact?WindowManager.LayoutParams.WRAP_CONTENT:(int)(screenHeight*.88f);
    WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
    p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.x=prefs.getInt("overlay_x",0);p.y=prefs.getInt("overlay_y",dp(24));return p;
  }
  private void hideOverlay(){if(overlay!=null&&overlay.getWindowToken()!=null)try{wm.removeView(overlay);}catch(Exception ignored){}}
  private void setCompact(boolean value){
    compact=value;overlayBody.setVisibility(value?android.view.View.GONE:android.view.View.VISIBLE);
    actions.setVisibility(value?android.view.View.GONE:android.view.View.VISIBLE);
    compactSummary.setVisibility(value?android.view.View.VISIBLE:android.view.View.GONE);
    resizeButton.setText(value?"+":"−");
    if(overlay.getWindowToken()!=null){
      WindowManager.LayoutParams p=(WindowManager.LayoutParams)overlay.getLayoutParams();
      p.width=value?Math.min(dp(360),getResources().getDisplayMetrics().widthPixels-dp(24)):-1;
      p.height=value?WindowManager.LayoutParams.WRAP_CONTENT:(int)(getResources().getDisplayMetrics().heightPixels*.88f);
      try{wm.updateViewLayout(overlay,p);}catch(Exception ignored){}
    }
  }
  private boolean dragOverlay(MotionEvent event){
    if(overlay==null||overlay.getWindowToken()==null)return false;
    WindowManager.LayoutParams p=(WindowManager.LayoutParams)overlay.getLayoutParams();
    if(event.getAction()==MotionEvent.ACTION_DOWN){dragStartX=event.getRawX();dragStartY=event.getRawY();dragOriginX=p.x;dragOriginY=p.y;return true;}
    if(event.getAction()==MotionEvent.ACTION_MOVE){
      int sw=getResources().getDisplayMetrics().widthPixels,sh=getResources().getDisplayMetrics().heightPixels;
      int width=compact?p.width:sw;int horizontalLimit=Math.max(0,(sw-width)/2);
      p.x=Math.max(-horizontalLimit,Math.min(horizontalLimit,dragOriginX+(int)(event.getRawX()-dragStartX)));
      int minY=dp(12),maxY=Math.max(minY,sh-overlay.getHeight()-dp(48));
      p.y=Math.max(minY,Math.min(maxY,dragOriginY+(int)(event.getRawY()-dragStartY)));
      try{wm.updateViewLayout(overlay,p);}catch(Exception ignored){}return true;
    }
    if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL){
      getSharedPreferences(MainActivity.PREF,MODE_PRIVATE).edit().putInt("overlay_x",p.x).putInt("overlay_y",p.y).apply();return true;
    }
    return true;
  }
  private void playSignal(){try{android.media.ToneGenerator t=new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,65);t.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,120);new android.os.Handler(getMainLooper()).postDelayed(t::release,500);}catch(Exception ignored){}}
  private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
  @Override public void onInterrupt(){hideOverlay();}
  @Override public void onDestroy(){hideOverlay();super.onDestroy();}
}
