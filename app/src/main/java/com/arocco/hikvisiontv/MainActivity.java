package com.arocco.hikvisiontv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
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

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private final List<ExoPlayer> players = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root;
    private boolean inSettings = false;
    private ExoPlayer probePlayer;
    private int probeGeneration = 0;

    private static class Candidate {
        final int port;
        final String style;
        final boolean main;
        Candidate(int port, String style, boolean main) {
            this.port = port;
            this.style = style;
            this.main = main;
        }
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        prefs = getSharedPreferences("hikvision_tv", MODE_PRIVATE);
        if (prefs.getString("host", "").isEmpty()) showSettings(); else showLive();
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        t.setPadding(12, 8, 12, 8);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setFocusable(true);
        return b;
    }

    private EditText field(String hint, String val) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(val);
        e.setSingleLine();
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        e.setBackgroundColor(Color.rgb(35, 39, 46));
        e.setPadding(18, 4, 18, 4);
        e.setFocusable(true);
        return e;
    }

    private void base() {
        releasePlayers();
        inSettings = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 18, 28, 18);
        root.setBackgroundColor(Color.rgb(16, 18, 22));
        setContentView(root);
    }

    private String normalizeHost(String raw) {
        String h = raw == null ? "" : raw.trim();
        h = h.replace("rtsp://", "").replace("http://", "").replace("https://", "");
        int slash = h.indexOf('/');
        if (slash >= 0) h = h.substring(0, slash);
        int at = h.lastIndexOf('@');
        if (at >= 0) h = h.substring(at + 1);
        if (h.startsWith("[") && h.contains("]")) return h;
        int colon = h.lastIndexOf(':');
        if (colon > 0 && h.indexOf(':') == colon) {
            String maybePort = h.substring(colon + 1);
            if (maybePort.matches("\\d+")) h = h.substring(0, colon);
        }
        return h.trim();
    }

    private void saveSettings(EditText host, EditText port, EditText user, EditText pass, EditText channels, CheckBox mainGrid) {
        int p = 554, c = 4;
        try { p = Integer.parseInt(port.getText().toString().trim()); } catch (Exception ignored) {}
        try { c = Integer.parseInt(channels.getText().toString().trim()); } catch (Exception ignored) {}
        c = Math.max(1, Math.min(16, c));
        prefs.edit()
                .putString("host", normalizeHost(host.getText().toString()))
                .putInt("port", p)
                .putString("user", user.getText().toString().trim())
                .putString("pass", pass.getText().toString())
                .putInt("channels", c)
                .putBoolean("gridMain", mainGrid.isChecked())
                .apply();
    }

    private void showSettings() {
        releasePlayers();
        inSettings = true;
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(16, 18, 22));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(48, 28, 48, 28);
        scroll.addView(body);
        setContentView(scroll);

        body.addView(text("HikVision TV v1.2 - Connection Settings", 28));
        TextView help = text("Use the LOCAL IP of the DVR/NVR. RTSP is commonly 554 or 10554. Server Port 8000 is not RTSP.", 15);
        help.setTextColor(Color.LTGRAY);
        body.addView(help);

        EditText host = field("DVR / NVR IP (example: 192.168.1.64)", prefs.getString("host", ""));
        body.addView(host, new LinearLayout.LayoutParams(-1, 62));
        EditText port = field("RTSP Port", String.valueOf(prefs.getInt("port", 554)));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        body.addView(port, new LinearLayout.LayoutParams(-1, 62));
        EditText user = field("Username", prefs.getString("user", "admin"));
        body.addView(user, new LinearLayout.LayoutParams(-1, 62));
        EditText pass = field("Password", prefs.getString("pass", ""));
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(pass, new LinearLayout.LayoutParams(-1, 62));
        EditText channels = field("Channels (1-16)", String.valueOf(prefs.getInt("channels", 4)));
        channels.setInputType(InputType.TYPE_CLASS_NUMBER);
        body.addView(channels, new LinearLayout.LayoutParams(-1, 62));

        CheckBox mainGrid = new CheckBox(this);
        mainGrid.setText("Use MAIN stream in grid (Sub is recommended)");
        mainGrid.setTextColor(Color.WHITE);
        mainGrid.setChecked(prefs.getBoolean("gridMain", false));
        mainGrid.setFocusable(true);
        body.addView(mainGrid, new LinearLayout.LayoutParams(-1, 58));

        TextView testStatus = text("Tip: use Auto Detect CH1 first.", 15);
        testStatus.setTextColor(Color.rgb(120, 190, 255));
        body.addView(testStatus);

        Button auto = button("AUTO DETECT CH1");
        body.addView(auto, new LinearLayout.LayoutParams(-1, 72));

        LinearLayout row = new LinearLayout(this);
        Button testPort = button("Test Port");
        Button testMain = button("Test Main");
        Button testSub = button("Test Sub");
        row.addView(testPort, new LinearLayout.LayoutParams(0, 64, 1));
        row.addView(testMain, new LinearLayout.LayoutParams(0, 64, 1));
        row.addView(testSub, new LinearLayout.LayoutParams(0, 64, 1));
        body.addView(row);

        Button save = button("Save & Open Live View");
        body.addView(save, new LinearLayout.LayoutParams(-1, 68));

        auto.setOnClickListener(v -> {
            saveSettings(host, port, user, pass, channels, mainGrid);
            showAutoDetect();
        });
        testPort.setOnClickListener(v -> {
            saveSettings(host, port, user, pass, channels, mainGrid);
            testTcpPort(testStatus);
        });
        testMain.setOnClickListener(v -> {
            saveSettings(host, port, user, pass, channels, mainGrid);
            showTest(true);
        });
        testSub.setOnClickListener(v -> {
            saveSettings(host, port, user, pass, channels, mainGrid);
            showTest(false);
        });
        save.setOnClickListener(v -> {
            saveSettings(host, port, user, pass, channels, mainGrid);
            showLive();
        });
        auto.requestFocus();
    }

    private void testTcpPort(TextView status) {
        String host = prefs.getString("host", "");
        int port = prefs.getInt("port", 554);
        status.setText("Testing " + host + ":" + port + " ...");
        new Thread(() -> {
            String result;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 4000);
                result = "RTSP PORT OPEN: " + host + ":" + port + " ✓";
            } catch (Exception e) {
                result = "PORT FAILED: " + host + ":" + port + " — " + safeMessage(e);
            }
            String finalResult = result;
            runOnUiThread(() -> status.setText(finalResult));
        }).start();
    }

    private String pathFor(String style, int id) {
        if ("upper".equals(style)) return "/Streaming/Channels/" + id;
        if ("isapi".equals(style)) return "/ISAPI/Streaming/channels/" + id;
        return "/Streaming/channels/" + id;
    }

    private String rtspFor(int ch, boolean main, int port, String style) {
        String host = prefs.getString("host", "");
        String user = prefs.getString("user", "admin");
        String pass = prefs.getString("pass", "");
        int id = ch * 100 + (main ? 1 : 2);
        String auth = "";
        if (!user.isEmpty()) auth = Uri.encode(user) + ":" + Uri.encode(pass) + "@";
        return "rtsp://" + auth + host + ":" + port + pathFor(style, id);
    }

    private String rtsp(int ch, boolean main) {
        return rtspFor(ch, main, prefs.getInt("port", 554), prefs.getString("pathStyle", "lower"));
    }

    private String displayRtspFor(int ch, boolean main, int port, String style) {
        String host = prefs.getString("host", "");
        String user = prefs.getString("user", "admin");
        int id = ch * 100 + (main ? 1 : 2);
        return "rtsp://" + (user.isEmpty() ? "" : user + ":***@") + host + ":" + port + pathFor(style, id);
    }

    private String displayRtsp(int ch, boolean main) {
        return displayRtspFor(ch, main, prefs.getInt("port", 554), prefs.getString("pathStyle", "lower"));
    }

    private ExoPlayer makePlayer(PlayerView view, int ch, boolean main, TextView status) {
        ExoPlayer p = new ExoPlayer.Builder(this).build();
        RtspMediaSource src = new RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(8000)
                .createMediaSource(MediaItem.fromUri(Uri.parse(rtsp(ch, main))));
        view.setPlayer(p);
        view.setUseController(false);
        p.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING && status != null) status.setText("Connecting " + (main ? "MAIN" : "SUB") + " ...");
                if (state == Player.STATE_READY && status != null) {
                    status.setText("CONNECTED ✓  " + displayRtsp(ch, main));
                    status.setTextColor(Color.rgb(110, 230, 140));
                }
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (status != null) {
                    status.setText(buildFriendlyError(error, ch, main));
                    status.setTextColor(Color.rgb(255, 120, 120));
                }
            }
        });
        p.setMediaSource(src);
        p.setRepeatMode(Player.REPEAT_MODE_ONE);
        p.prepare();
        p.play();
        players.add(p);
        return p;
    }

    private List<Candidate> buildCandidates() {
        List<Candidate> out = new ArrayList<>();
        Set<Integer> ports = new LinkedHashSet<>();
        ports.add(prefs.getInt("port", 554));
        ports.add(554);
        ports.add(10554);
        String[] styles = new String[]{"lower", "upper", "isapi"};
        for (int p : ports) {
            for (String style : styles) {
                out.add(new Candidate(p, style, true));
                out.add(new Candidate(p, style, false));
            }
        }
        return out;
    }

    private void showAutoDetect() {
        base();
        int generation = ++probeGeneration;
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Auto Detect Hikvision CH1", 22);
        Button back = button("Back");
        bar.addView(title, new LinearLayout.LayoutParams(0, 58, 1));
        bar.addView(back, new LinearLayout.LayoutParams(160, 58));
        root.addView(bar);

        TextView status = text("Starting automatic stream detection...", 16);
        status.setTextColor(Color.rgb(120, 190, 255));
        root.addView(status, new LinearLayout.LayoutParams(-1, 120));
        PlayerView pv = new PlayerView(this);
        root.addView(pv, new LinearLayout.LayoutParams(-1, 0, 1));
        Button open = button("Open Live View");
        open.setEnabled(false);
        root.addView(open, new LinearLayout.LayoutParams(-1, 64));

        back.setOnClickListener(v -> { ++probeGeneration; showSettings(); });
        open.setOnClickListener(v -> showLive());
        List<Candidate> candidates = buildCandidates();
        tryCandidate(generation, 0, candidates, pv, status, open);
        back.requestFocus();
    }

    private void tryCandidate(int generation, int index, List<Candidate> candidates, PlayerView pv, TextView status, Button open) {
        if (generation != probeGeneration) return;
        if (probePlayer != null) {
            try { probePlayer.release(); } catch (Exception ignored) {}
            probePlayer = null;
        }
        if (index >= candidates.size()) {
            status.setTextColor(Color.rgb(255, 120, 120));
            status.setText("NO H.264 STREAM FOUND. RTSP port may be open, but none of the standard Hikvision streams could be played.\n\nMost likely causes: 1) stream codec is H.265/H.265+, 2) username/password is rejected, 3) device uses a custom RTSP port/path.\n\nSet CH1 Main and Sub video encoding to H.264 in the Hikvision recorder/camera and run Auto Detect again.");
            return;
        }

        Candidate c = candidates.get(index);
        String shown = displayRtspFor(1, c.main, c.port, c.style);
        status.setTextColor(Color.rgb(120, 190, 255));
        status.setText("Trying " + (index + 1) + "/" + candidates.size() + "\n" + shown);

        ExoPlayer p = new ExoPlayer.Builder(this).build();
        probePlayer = p;
        pv.setPlayer(p);
        pv.setUseController(false);
        boolean[] finished = new boolean[]{false};

        Runnable next = () -> {
            if (generation != probeGeneration || finished[0]) return;
            finished[0] = true;
            try { p.release(); } catch (Exception ignored) {}
            if (probePlayer == p) probePlayer = null;
            handler.postDelayed(() -> tryCandidate(generation, index + 1, candidates, pv, status, open), 250);
        };

        p.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (generation != probeGeneration || finished[0]) return;
                if (state == Player.STATE_READY) {
                    finished[0] = true;
                    prefs.edit().putInt("port", c.port).putString("pathStyle", c.style).apply();
                    status.setTextColor(Color.rgb(110, 230, 140));
                    status.setText("FOUND ✓\n" + shown + "\n\nThis RTSP stream is playable. Settings were saved automatically.");
                    open.setEnabled(true);
                    open.requestFocus();
                }
            }
            @Override public void onPlayerError(PlaybackException error) {
                next.run();
            }
        });

        RtspMediaSource src = new RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(5000)
                .createMediaSource(MediaItem.fromUri(Uri.parse(rtspFor(1, c.main, c.port, c.style))));
        p.setMediaSource(src);
        p.prepare();
        p.play();
        handler.postDelayed(next, 6500);
    }

    private String buildFriendlyError(Throwable error, int ch, boolean main) {
        String details = fullError(error);
        String low = details.toLowerCase();
        String hint;
        if (low.contains("401") || low.contains("unauthorized") || low.contains("authentication")) {
            hint = "Username/password rejected.";
        } else if (low.contains("404") || low.contains("not found")) {
            hint = "Stream path/channel not found.";
        } else if (low.contains("connect") || low.contains("timeout") || low.contains("refused") || low.contains("unreachable")) {
            hint = "Cannot reach RTSP stream. Try 554 or 10554.";
        } else if (low.contains("decoder") || low.contains("format") || low.contains("unsupported") || low.contains("codec") || low.contains("h265") || low.contains("hevc")) {
            hint = "Set Hikvision stream codec to H.264 (not H.265/H.265+).";
        } else {
            hint = "Check login/path and set stream codec to H.264.";
        }
        return "CH " + ch + " " + (main ? "MAIN" : "SUB") + " ERROR\n" + hint + "\n" + shortText(details, 220);
    }

    private String fullError(Throwable e) {
        StringBuilder b = new StringBuilder();
        Throwable x = e;
        int n = 0;
        while (x != null && n < 5) {
            if (n > 0) b.append(" | ");
            b.append(x.getClass().getSimpleName()).append(": ").append(safeMessage(x));
            x = x.getCause();
            n++;
        }
        return b.toString();
    }

    private String safeMessage(Throwable e) {
        String m = e == null ? "" : e.getMessage();
        return (m == null || m.trim().isEmpty()) ? (e == null ? "Unknown error" : e.getClass().getSimpleName()) : m;
    }

    private String shortText(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private void showTest(boolean main) {
        base();
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("Back to Settings");
        bar.addView(text("CH1 " + (main ? "MAIN" : "SUB") + " test", 22), new LinearLayout.LayoutParams(0, 58, 1));
        bar.addView(back, new LinearLayout.LayoutParams(220, 58));
        root.addView(bar);
        TextView status = text("Testing: " + displayRtsp(1, main), 16);
        status.setTextColor(Color.rgb(120, 190, 255));
        root.addView(status, new LinearLayout.LayoutParams(-1, 110));
        PlayerView pv = new PlayerView(this);
        root.addView(pv, new LinearLayout.LayoutParams(-1, 0, 1));
        makePlayer(pv, 1, main, status);
        back.setOnClickListener(v -> showSettings());
        back.requestFocus();
    }

    private void showLive() {
        base();
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(text("HikVision TV v1.2", 24), new LinearLayout.LayoutParams(0, 56, 1));
        Button settings = button("Settings");
        bar.addView(settings, new LinearLayout.LayoutParams(160, 56));
        settings.setOnClickListener(v -> showSettings());
        root.addView(bar);

        int n = prefs.getInt("channels", 4);
        boolean gridMain = prefs.getBoolean("gridMain", false);
        int cols = n <= 1 ? 1 : n <= 4 ? 2 : n <= 9 ? 3 : 4;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(cols);
        grid.setRowCount((n + cols - 1) / cols);
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        for (int i = 1; i <= n; i++) {
            final int ch = i;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setBackgroundColor(Color.BLACK);
            tile.setPadding(2, 2, 2, 2);
            tile.setFocusable(true);
            PlayerView pv = new PlayerView(this);
            tile.addView(pv, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView lab = text("CH " + i + " - connecting...", 12);
            lab.setBackgroundColor(Color.rgb(25, 25, 25));
            tile.addView(lab, new LinearLayout.LayoutParams(-1, 62));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            lp.setMargins(3, 3, 3, 3);
            grid.addView(tile, lp);
            makePlayer(pv, i, gridMain, lab);
            tile.setOnFocusChangeListener((v, f) -> {
                v.setBackgroundColor(f ? Color.rgb(63, 140, 255) : Color.BLACK);
                v.setScaleX(f ? 1.01f : 1f);
                v.setScaleY(f ? 1.01f : 1f);
            });
            tile.setOnClickListener(v -> showFullscreen(ch));
            if (i == 1) tile.requestFocus();
        }
    }

    private void showFullscreen(int ch) {
        releasePlayers();
        inSettings = false;
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setBackgroundColor(Color.BLACK);
        setContentView(f);
        TextView label = text("CH " + ch + " MAIN - connecting...   (BACK to grid)", 15);
        f.addView(label, new LinearLayout.LayoutParams(-1, 80));
        PlayerView pv = new PlayerView(this);
        pv.setFocusable(true);
        f.addView(pv, new LinearLayout.LayoutParams(-1, 0, 1));
        makePlayer(pv, ch, true, label);
        pv.requestFocus();
    }

    @Override
    public void onBackPressed() {
        ++probeGeneration;
        if (inSettings || prefs.getString("host", "").isEmpty()) super.onBackPressed();
        else showLive();
    }

    private void releasePlayers() {
        ++probeGeneration;
        if (probePlayer != null) {
            try { probePlayer.release(); } catch (Exception ignored) {}
            probePlayer = null;
        }
        for (ExoPlayer p : players) {
            try { p.release(); } catch (Exception ignored) {}
        }
        players.clear();
    }

    @Override
    protected void onDestroy() {
        releasePlayers();
        super.onDestroy();
    }
}
