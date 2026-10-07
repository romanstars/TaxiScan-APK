package com.taxiscan.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaxiAccessibilityService extends AccessibilityService {
  private WindowManager wm; private View overlay; private TextView title,result,detail; private long lastEventAt=0; private String lastSignature="";
  private static final Pattern MONEY=Pattern.compile("(?i)(?:₴|грн|uah)\\s*(\\d{1,4}(?:[.,]\\d{1,2})?)|(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(?:₴|грн|uah)");
  private static final Pattern KM=Pattern.compile("(?i)(\\d{1,2}(?:[.,]\\d{1,2})?)\\s*км");
  @Override public void onServiceConnected(){super.onServiceConnected();wm=(WindowManager)getSystemService(WINDOW_SERVICE);}
  @Override public void onAccessibilityEvent(AccessibilityEvent event){
    if(event==null||event.getPackageName()==null)return;
    String pkg=event.getPackageName().toString();String service=serviceName(pkg);if(service==null||!serviceEnabled(service))return;
    SharedPreferences p=getSharedPreferences(MainActivity.PREF,MODE_PRIVATE);if(p.getBoolean("paused",false))return;
    StringBuilder text=new StringBuilder();for(CharSequence x:event.getText())if(x!=null)text.append(x).append(' ');AccessibilityNodeInfo root=getRootInActiveWindow();if(root!=null)collect(root,text,0);
    String raw=text.toString().replaceAll("\\s+"," ").trim();if(raw.length()<5)return;
    double amount=extract(MONEY,raw),distance=extract(KM,raw);if(amount<=0)return;
    String sig=service+":"+amount+":"+distance+":"+raw.hashCode();long now=SystemClock.elapsedRealtime();if(sig.equals(lastSignature)&&now-lastEventAt<2500)return;lastSignature=sig;lastEventAt=now;
    double pickup=pickup(raw);double rating=rating(raw);double commission=p.getFloat("commission",15f),cost=p.getFloat("cost_km",2.5f);double net=amount*(1-commission/100.0)-(distance+pickup)*cost;double perKm=(distance+pickup)>0?net/(distance+pickup):0;double hourly=distance>0?net/(distance/25.0):0;
    boolean good=amount>=p.getInt("min_fare",80)&&perKm>=p.getInt("min_km",10)&&hourly>=p.getInt("min_hour",250)&&pickup<=p.getInt("max_pickup",3)&&(rating<=0||rating>=p.getInt("min_rating",46)/10.0);
    JSONObject item=new JSONObject();try{item.put("service",service);item.put("amount",amount);item.put("distance",distance);item.put("pickup",pickup);item.put("net",net);item.put("perKm",perKm);item.put("hourly",hourly);item.put("rating",rating);item.put("good",good);item.put("time",new SimpleDateFormat("HH:mm:ss",new Locale("uk")).format(new Date()));item.put("iso",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).format(new Date()));}catch(Exception ignored){}
    JSONArray a;try{a=new JSONArray(p.getString("events","[]"));}catch(Exception e){a=new JSONArray();}a.put(item);while(a.length()>500){JSONArray smaller=new JSONArray();for(int i=1;i<a.length();i++)smaller.put(a.opt(i));a=smaller;}p.edit().putString("events",a.toString()).apply();
    if(!p.getBoolean("only_good",true)||good)showOverlay(service,amount,distance,pickup,net,perKm,hourly,good);
    if(p.getBoolean("sound",true))playSignal();
  }
  private String serviceName(String pkg){String s=pkg.toLowerCase(Locale.US);if(s.contains("bolt")||s.contains("mtakso"))return "Bolt";if(s.contains("uklon"))return "Uklon";if(s.contains("uber"))return "Uber";return null;}
  private boolean serviceEnabled(String service){String key="service_"+(service.toLowerCase(Locale.US).contains("uber")?"uber":service.toLowerCase(Locale.US).replace(" ",""));return getSharedPreferences(MainActivity.PREF,MODE_PRIVATE).getBoolean(key,!service.equals("Uber"));}
  private void collect(AccessibilityNodeInfo node,StringBuilder out,int depth){if(node==null||depth>22||out.length()>10000)return;CharSequence t=node.getText(),d=node.getContentDescription();if(t!=null&&t.length()>0)out.append(t).append(' ');if(d!=null&&d.length()>0)out.append(d).append(' ');for(int i=0;i<node.getChildCount();i++)collect(node.getChild(i),out,depth+1);}
  private double extract(Pattern pattern,String text){Matcher m=pattern.matcher(text);if(!m.find())return 0;String s=m.group(1)!=null?m.group(1):m.group(2);try{return Double.parseDouble(s.replace(',','.'));}catch(Exception e){return 0;}}
  private double rating(String text){Matcher m=Pattern.compile("(?i)(?:рейтинг|rating)\\D{0,8}(\\d[.,]\\d)").matcher(text);if(!m.find())return 0;try{return Double.parseDouble(m.group(1).replace(',','.'));}catch(Exception e){return 0;}}
  private double pickup(String text){Matcher m=Pattern.compile("(?i)подач[аіу]?\\D{0,20}(\\d{1,2}(?:[.,]\\d{1,2})?)\\s*км").matcher(text);if(!m.find())return 0;try{return Double.parseDouble(m.group(1).replace(',','.'));}catch(Exception e){return 0;}}
  private void showOverlay(String service,double amount,double distance,double pickup,double net,double perKm,double hourly,boolean good){if(Build.VERSION.SDK_INT>=23&&!android.provider.Settings.canDrawOverlays(this))return;if(wm==null)wm=(WindowManager)getSystemService(WINDOW_SERVICE);if(overlay==null)createOverlay();title.setText(service+"  •  "+String.format(new Locale("uk","UA"),"%.0f ₴",amount));result.setText((good?"✓ ВИГІДНО":"× НЕВИГІДНО")+"   "+String.format(new Locale("uk","UA"),"%.1f ₴/км",perKm));result.setTextColor(good?0xff33d385:0xfff47067);detail.setText("Чистими "+String.format(new Locale("uk","UA"),"%.0f ₴",net)+"  •  "+String.format(new Locale("uk","UA"),"%.1f км",distance)+"  •  подача "+String.format(new Locale("uk","UA"),"%.1f км",pickup)+"\n"+String.format(new Locale("uk","UA"),"%.0f ₴/год",hourly));if(overlay.getWindowToken()==null){WindowManager.LayoutParams lp=params();try{wm.addView(overlay,lp);}catch(Exception ignored){}}}
  private void createOverlay(){LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(14),dp(10),dp(14),dp(10));GradientDrawable shape=new GradientDrawable();shape.setColor(0xee111815);shape.setCornerRadius(dp(16));shape.setStroke(dp(1),0xff33d385);box.setBackground(shape);LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);title=new TextView(this);title.setTextColor(Color.WHITE);title.setTextSize(15);title.setTypeface(null,Typeface.BOLD);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView close=new TextView(this);close.setText("×");close.setTextColor(Color.WHITE);close.setTextSize(24);close.setOnClickListener(v->hideOverlay());top.addView(close);box.addView(top);result=new TextView(this);result.setTextSize(16);result.setTypeface(null,Typeface.BOLD);result.setPadding(0,dp(4),0,dp(4));box.addView(result);detail=new TextView(this);detail.setTextColor(0xffb0c1b8);detail.setTextSize(12);box.addView(detail);overlay=box;}
  private WindowManager.LayoutParams params(){int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,WindowManager.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;lp.y=dp(28);return lp;}
  private void hideOverlay(){if(overlay!=null&&overlay.getWindowToken()!=null)try{wm.removeView(overlay);}catch(Exception ignored){}}
  private void playSignal(){try{android.media.ToneGenerator t=new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,65);t.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,120);new android.os.Handler(getMainLooper()).postDelayed(t::release,500);}catch(Exception ignored){}}
  private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  @Override public void onInterrupt(){hideOverlay();}
  @Override public void onDestroy(){hideOverlay();super.onDestroy();}
}
