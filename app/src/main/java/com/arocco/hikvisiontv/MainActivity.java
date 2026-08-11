package com.arocco.hikvisiontv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.PlayerView;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private final List<ExoPlayer> players = new ArrayList<>();
    private SharedPreferences prefs;
    private LinearLayout root;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        prefs = getSharedPreferences("hikvision_tv", MODE_PRIVATE);
        if (prefs.getString("host", "").isEmpty()) showSettings(); else showLive();
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(sp); t.setPadding(12,8,12,8); return t;
    }
    private Button button(String s) {
        Button b=new Button(this); b.setText(s); b.setFocusable(true); return b;
    }
    private EditText field(String hint, String val) {
        EditText e=new EditText(this); e.setHint(hint); e.setText(val); e.setSingleLine(); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); e.setBackgroundColor(Color.rgb(35,39,46)); e.setPadding(18,4,18,4); e.setFocusable(true); return e;
    }

    private void base() {
        releasePlayers();
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,18,28,18); root.setBackgroundColor(Color.rgb(16,18,22)); setContentView(root);
    }

    private void showSettings() {
        releasePlayers();
        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(Color.rgb(16,18,22));
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(48,28,48,28); scroll.addView(body); setContentView(scroll);
        body.addView(text("HikVision TV - Settings",28));
        body.addView(text("Direct RTSP connection to Hikvision DVR/NVR",16));
        EditText host=field("DVR / NVR IP", prefs.getString("host","")); body.addView(host, new LinearLayout.LayoutParams(-1,60));
        EditText port=field("RTSP Port", String.valueOf(prefs.getInt("port",554))); body.addView(port,new LinearLayout.LayoutParams(-1,60));
        EditText user=field("Username", prefs.getString("user","admin")); body.addView(user,new LinearLayout.LayoutParams(-1,60));
        EditText pass=field("Password", prefs.getString("pass","")); pass.setInputType(0x00000081); body.addView(pass,new LinearLayout.LayoutParams(-1,60));
        EditText channels=field("Channels (1-16)", String.valueOf(prefs.getInt("channels",4))); channels.setInputType(2); body.addView(channels,new LinearLayout.LayoutParams(-1,60));
        Button save=button("Save & Open Live View"); body.addView(save,new LinearLayout.LayoutParams(-1,64));
        save.setOnClickListener(v->{
            int p=554,c=4; try{p=Integer.parseInt(port.getText().toString());}catch(Exception ignored){} try{c=Integer.parseInt(channels.getText().toString());}catch(Exception ignored){} c=Math.max(1,Math.min(16,c));
            prefs.edit().putString("host",host.getText().toString().trim()).putInt("port",p).putString("user",user.getText().toString().trim()).putString("pass",pass.getText().toString()).putInt("channels",c).apply();
            showLive();
        });
        host.requestFocus();
    }

    private String rtsp(int ch, boolean main) {
        String host=prefs.getString("host",""); int port=prefs.getInt("port",554); String user=prefs.getString("user","admin"); String pass=prefs.getString("pass","");
        String u=URLEncoder.encode(user, StandardCharsets.UTF_8); String pw=URLEncoder.encode(pass, StandardCharsets.UTF_8);
        int id=ch*100+(main?1:2);
        return "rtsp://"+u+":"+pw+"@"+host+":"+port+"/Streaming/Channels/"+id;
    }

    private ExoPlayer makePlayer(PlayerView view, int ch, boolean main) {
        ExoPlayer p=new ExoPlayer.Builder(this).build();
        RtspMediaSource src=new RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(MediaItem.fromUri(Uri.parse(rtsp(ch,main))));
        view.setPlayer(p); view.setUseController(false); p.setMediaSource(src); p.setRepeatMode(Player.REPEAT_MODE_ONE); p.prepare(); p.play(); players.add(p); return p;
    }

    private void showLive() {
        base();
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.addView(text("HikVision TV",24),new LinearLayout.LayoutParams(0,56,1));
        Button settings=button("Settings"); bar.addView(settings,new LinearLayout.LayoutParams(150,56)); settings.setOnClickListener(v->showSettings()); root.addView(bar);
        int n=prefs.getInt("channels",4); int cols=n<=1?1:n<=4?2:n<=9?3:4;
        GridLayout grid=new GridLayout(this); grid.setColumnCount(cols); grid.setRowCount((n+cols-1)/cols); root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=1;i<=n;i++){
            final int ch=i; LinearLayout tile=new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setBackgroundColor(Color.BLACK); tile.setPadding(2,2,2,2); tile.setFocusable(true);
            PlayerView pv=new PlayerView(this); tile.addView(pv,new LinearLayout.LayoutParams(-1,0,1)); TextView lab=text("CH "+i,14); lab.setBackgroundColor(Color.rgb(25,25,25)); tile.addView(lab,new LinearLayout.LayoutParams(-1,38));
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=0; lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1,1f); lp.rowSpec=GridLayout.spec(GridLayout.UNDEFINED,1,1f); lp.setMargins(3,3,3,3); grid.addView(tile,lp);
            makePlayer(pv,i,false);
            tile.setOnFocusChangeListener((v,f)->{v.setBackgroundColor(f?Color.rgb(63,140,255):Color.BLACK); v.setScaleX(f?1.01f:1f); v.setScaleY(f?1.01f:1f);});
            tile.setOnClickListener(v->showFullscreen(ch)); if(i==1) tile.requestFocus();
        }
    }

    private void showFullscreen(int ch) {
        releasePlayers();
        LinearLayout f=new LinearLayout(this); f.setOrientation(LinearLayout.VERTICAL); f.setBackgroundColor(Color.BLACK); setContentView(f);
        TextView label=text("CH "+ch+"  •  Main Stream   (BACK to grid)",16); f.addView(label,new LinearLayout.LayoutParams(-1,48));
        PlayerView pv=new PlayerView(this); pv.setFocusable(true); f.addView(pv,new LinearLayout.LayoutParams(-1,0,1)); makePlayer(pv,ch,true); pv.requestFocus();
    }

    @Override public void onBackPressed(){ if(prefs.getString("host","").isEmpty()) super.onBackPressed(); else showLive(); }
    private void releasePlayers(){ for(ExoPlayer p:players){ try{p.release();}catch(Exception ignored){} } players.clear(); }
    @Override protected void onDestroy(){ releasePlayers(); super.onDestroy(); }
}
