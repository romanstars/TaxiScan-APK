package com.taxiscan.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(17,24,39));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247,248,250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1,-2));

        TextView title = text("TaxiScan", 30, true);
        root.addView(title);
        TextView sub = text("Розумний помічник водія", 15, false);
        sub.setTextColor(Color.rgb(107,114,128));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1,-2);
        subLp.setMargins(0,dp(4),0,dp(22));
        root.addView(sub, subLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(18),dp(18),dp(18));
        card.setBackground(bg(Color.WHITE,20));
        root.addView(card, new LinearLayout.LayoutParams(-1,-2));

        TextView status = text("Готовий до роботи", 20, true);
        card.addView(status);
        TextView info = text("TaxiScan допоможе швидко оцінювати замовлення та витрати під час роботи.", 15, false);
        info.setTextColor(Color.rgb(75,85,99));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1,-2);
        infoLp.setMargins(0,dp(8),0,dp(16));
        card.addView(info, infoLp);

        Button start = new Button(this);
        start.setText("Почати сканування");
        start.setTextSize(16);
        start.setTextColor(Color.WHITE);
        start.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        start.setAllCaps(false);
        start.setBackground(bg(Color.rgb(17,24,39),16));
        start.setOnClickListener(v -> status.setText("Сканування увімкнено"));
        card.addView(start, new LinearLayout.LayoutParams(-1,dp(56)));

        TextView section = text("Швидкі показники", 20, true);
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(-1,-2);
        secLp.setMargins(0,dp(24),0,dp(10));
        root.addView(section, secLp);

        String[] stats = {"Витрата пального: —", "Комісія: —", "Чистий дохід: —", "Коефіцієнт замовлення: —"};
        for (String s : stats) {
            TextView row = text(s, 16, false);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16),0,dp(16),0);
            row.setBackground(bg(Color.WHITE,16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(58));
            lp.setMargins(0,0,0,dp(10));
            root.addView(row, lp);
        }

        TextView foot = text("Тестова збірка TaxiScan 1.0.0", 13, false);
        foot.setTextColor(Color.rgb(156,163,175));
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1,-2);
        footLp.setMargins(0,dp(18),0,0);
        root.addView(foot, footLp);

        setContentView(scroll);
    }
}
