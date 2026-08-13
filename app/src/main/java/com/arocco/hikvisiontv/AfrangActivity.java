package com.arocco.hikvisiontv;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.List;

public class AfrangActivity extends Activity {
    private final List<ExoPlayer> players = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private android.content.SharedPreferences p;
    private int layout = 8;
    private boolean fullscreen = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        p = getSharedPreferences("hikvision_tv", MODE_PRIVATE);
        showSplash();
    }

    private TextView txt(String s,int z){ TextView v=new TextView(this); v.setText(s); v.setTextSize(z); v.setTextColor(Color.WHITE); v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(10,6,10,6); return v; }
    private Button btn(String s){ Button b=new Button(this); b.setText(s); b.setFocusable(true); return b; }
    private EditText fld(String hint,String val){ EditText e=new EditText(this); e.setHint(hint); e.setText(val); e.setSingleLine(); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); e.setBackgroundColor(Color.rgb(35,39,46)); e.setPadding(16,4,16,4); e.setFocusable(true); return e; }

    private void showSplash(){
        release();
        LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL); r.setBackgroundColor(Color.rgb(10,12,16)); r.setPadding(42,42,42,42);
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER);
        TextView domain=txt("afrangdiesel.com",32); domain.setGravity(Gravity.CENTER); c.addView(domain,new LinearLayout.LayoutParams(-1,-2));
        TextView sub=txt("HikVision TV",17); sub.setGravity(Gravity.CENTER); sub.setTextColor(Color.rgb(130,175,255)); c.addView(sub,new LinearLayout.LayoutParams(-1,-2));
        r.addView(c,new LinearLayout.LayoutParams(-1,0,1));
        TextView f=txt("افرنگ دیزل",20); f.setGravity(Gravity.CENTER); r.addView(f,new LinearLayout.LayoutParams(-1,-2));
        setContentView(r);
        h.postDelayed(()->{ if(isFinishing()) return; if(p.getString("host","").isEmpty()) showSettings(); else showLive(); },1600);
    }

    private void showSettings(){
        release(); fullscreen=false;
        ScrollView s=new ScrollView(this); s.setBackgroundColor(Color.rgb(16,18,22));
        LinearLayout b=new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setPadding(48,28,48,28); s.addView(b); setContentView(s);
        b.addView(txt("Afrang Diesel • HikVision TV",28));
        EditText host=fld("DVR / NVR IP",p.getString("host","")); b.addView(host,new LinearLayout.LayoutParams(-1,62));
        EditText port=fld("RTSP Port",String.valueOf(p.getInt("port",554))); port.setInputType(InputType.TYPE_CLASS_NUMBER); b.addView(port,new LinearLayout.LayoutParams(-1,62));
        EditText user=fld("Username",p.getString("user","admin")); b.addView(user,new LinearLayout.LayoutParams(-1,62));
        EditText pass=fld("Password",p.getString("pass","")); pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); b.addView(pass,new LinearLayout.LayoutParams(-1,62));
        EditText channels=fld("Channels (1-16)",String.valueOf(p.getInt("channels",8))); channels.setInputType(InputType.TYPE_CLASS_NUMBER); b.addView(channels,new LinearLayout.LayoutParams(-1,62));
        CheckBox main=new CheckBox(this); main.setText("Use MAIN stream in grid"); main.setTextColor(Color.WHITE); main.setChecked(p.getBoolean("gridMain",false)); main.setFocusable(true); b.addView(main,new LinearLayout.LayoutParams(-1,58));
        Button save=btn("Save & Open Live View"); b.addView(save,new LinearLayout.LayoutParams(-1,68));
        save.setOnClickListener(v->{
            int po=554,ch=8; try{po=Integer.parseInt(port.getText().toString());}catch(Exception ignored){} try{ch=Integer.parseInt(channels.getText().toString());}catch(Exception ignored){} ch=Math.max(1,Math.min(16,ch));
            p.edit().putString("host",host.getText().toString().trim()).putInt("port",po).putString("user",user.getText().toString().trim()).putString("pass",pass.getText().toString()).putInt("channels",ch).putBoolean("gridMain",main.isChecked()).apply();
            layout=Math.min(layout,ch); showLive();
        });
        host.requestFocus();
    }

    private String path(int ch,boolean main){ int id=ch*100+(main?1:2); String style=p.getString("pathStyle","lower"); if("upper".equals(style)) return "/Streaming/Channels/"+id; if("isapi".equals(style)) return "/ISAPI/Streaming/channels/"+id; return "/Streaming/channels/"+id; }
    private String rtsp(int ch,boolean main){ String u=p.getString("user","admin"),pw=p.getString("pass",""); String auth=u.isEmpty()?"":Uri.encode(u)+":"+Uri.encode(pw)+"@"; return "rtsp://"+auth+p.getString("host","")+":"+p.getInt("port",554)+path(ch,main); }
    private String safeRtsp(int ch,boolean main){ String u=p.getString("user","admin"); return "rtsp://"+(u.isEmpty()?"":u+":***@")+p.getString("host","")+":"+p.getInt("port",554)+path(ch,main); }

    private ExoPlayer play(PlayerView pv,int ch,boolean main,TextView info){
        ExoPlayer x=new ExoPlayer.Builder(this).build();
        RtspMediaSource src=new RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(8000).createMediaSource(MediaItem.fromUri(Uri.parse(rtsp(ch,main))));
        pv.setPlayer(x); pv.setUseController(false);
        x.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){ if(state==Player.STATE_READY && info!=null){ info.setText("CH "+ch+" • Connected\n"+safeRtsp(ch,main)); info.setTextColor(Color.rgb(110,230,140)); info.setVisibility(View.GONE); } }
            @Override public void onPlayerError(PlaybackException e){ if(info!=null){ info.setText("CH "+ch+" • ERROR\n"+(e.getMessage()==null?"Connection error":e.getMessage())); info.setTextColor(Color.rgb(255,120,120)); info.setVisibility(View.VISIBLE); } }
        });
        x.setMediaSource(src); x.setRepeatMode(Player.REPEAT_MODE_ONE); x.prepare(); x.play(); x.setVolume(0f); players.add(x); return x;
    }

    private int cols(int n){ if(n==1)return 1; if(n==4)return 2; if(n==6)return 3; if(n==8)return 4; if(n==9)return 3; return 4; }

    private void showLive(){
        release(); fullscreen=false;
        int total=p.getInt("channels",8); if(layout>total) layout=total; if(layout<1)layout=1;
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(14,10,14,10); root.setBackgroundColor(Color.rgb(16,18,22)); setContentView(root);
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=txt("afrangdiesel.com",20); bar.addView(title,new LinearLayout.LayoutParams(0,54,1));
        int[] modes={1,4,6,8,9,16};
        for(int m:modes){ Button b=btn(String.valueOf(m)); b.setEnabled(m<=total); b.setOnClickListener(v->{layout=m;showLive();}); bar.addView(b,new LinearLayout.LayoutParams(72,50)); }
        Button set=btn("Settings"); set.setOnClickListener(v->showSettings()); bar.addView(set,new LinearLayout.LayoutParams(150,50)); root.addView(bar,new LinearLayout.LayoutParams(-1,58));

        GridLayout grid=new GridLayout(this); int c=cols(layout), rows=(layout+c-1)/c; grid.setColumnCount(c); grid.setRowCount(rows); grid.setBackgroundColor(Color.BLACK); root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        boolean main=p.getBoolean("gridMain",false);
        for(int i=1;i<=layout;i++) addTile(grid,i,c,main);
        TextView footer=txt("افرنگ دیزل",17); footer.setGravity(Gravity.CENTER); footer.setTextColor(Color.rgb(220,220,220)); root.addView(footer,new LinearLayout.LayoutParams(-1,34));
        if(grid.getChildCount()>0) grid.getChildAt(0).requestFocus();
    }

    private void addTile(GridLayout grid,int ch,int c,boolean main){
        LinearLayout tile=new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setBackgroundColor(Color.BLACK); tile.setFocusable(true); tile.setPadding(2,2,2,2);
        PlayerView pv=new PlayerView(this); tile.addView(pv,new LinearLayout.LayoutParams(-1,0,1));
        TextView info=txt("",11); info.setBackgroundColor(Color.argb(190,10,12,16)); info.setVisibility(View.GONE); tile.addView(info,new LinearLayout.LayoutParams(-1,-2));
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0;lp.height=0;lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1,1f);lp.rowSpec=GridLayout.spec(GridLayout.UNDEFINED,1,1f);lp.setMargins(3,3,3,3);grid.addView(tile,lp);
        play(pv,ch,main,info);
        final long[] last={0}; final Runnable[] one={null};
        Runnable click=()->{ long now=System.currentTimeMillis(); if(now-last[0]<350){ if(one[0]!=null)h.removeCallbacks(one[0]);last[0]=0;toggleInfo(info);}else{last[0]=now;one[0]=()->{if(last[0]!=0){last[0]=0;showFull(ch);}};h.postDelayed(one[0],320);} };
        tile.setOnClickListener(v->click.run());
        tile.setOnKeyListener((v,key,e)->{ if(e.getAction()==KeyEvent.ACTION_UP&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){click.run();return true;}return false;});
        tile.setOnLongClickListener(v->{toggleInfo(info);return true;});
        tile.setOnFocusChangeListener((v,f)->{v.setBackgroundColor(f?Color.rgb(63,140,255):Color.BLACK);v.setScaleX(f?1.01f:1f);v.setScaleY(f?1.01f:1f);});
    }

    private void toggleInfo(TextView i){ if(i.getVisibility()==View.VISIBLE)i.setVisibility(View.GONE); else {i.setVisibility(View.VISIBLE);h.postDelayed(()->i.setVisibility(View.GONE),4500);} }

    private void showFull(int ch){
        release(); fullscreen=true;
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setBackgroundColor(Color.BLACK);setContentView(r);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(txt("CH "+ch,18),new LinearLayout.LayoutParams(0,56,1));TextView brand=txt("afrangdiesel.com",14);top.addView(brand,new LinearLayout.LayoutParams(-2,56));r.addView(top,new LinearLayout.LayoutParams(-1,56));
        PlayerView pv=new PlayerView(this);pv.setFocusable(true);r.addView(pv,new LinearLayout.LayoutParams(-1,0,1));TextView info=txt("",12);info.setBackgroundColor(Color.argb(190,10,12,16));info.setVisibility(View.GONE);r.addView(info,new LinearLayout.LayoutParams(-1,-2));ExoPlayer x=play(pv,ch,true,info);x.setVolume(1f);
        final long[] last={0};pv.setOnClickListener(v->{long now=System.currentTimeMillis();if(now-last[0]<350){last[0]=0;toggleInfo(info);}else last[0]=now;});pv.setOnLongClickListener(v->{toggleInfo(info);return true;});pv.requestFocus();
    }

    @Override public void onBackPressed(){ if(fullscreen){showLive();return;} super.onBackPressed(); }
    private void release(){ for(ExoPlayer x:players)try{x.release();}catch(Exception ignored){} players.clear(); }
    @Override protected void onDestroy(){release();super.onDestroy();}
}
