package com.kts.fileexplorer.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.kts.fileexplorer.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Built-in full-featured accessible media player for Audio and Video.
 * Supports Next, Previous, 10s Rewind, 10s Fast Forward, scrubbing, and screen-reader accessibility.
 */
public class MediaPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_CURRENT_PATH = "extra_current_path";
    public static final String EXTRA_PLAYLIST_PATHS = "extra_playlist_paths";
    public static final String EXTRA_START_INDEX = "extra_start_index";

    private static final int SEEK_STEP_MS = 10000; // 10 seconds rewind/forward

    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus", "wma", "mid", "amr", "m4b", "aiff"
    ));
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "mkv", "webm", "avi", "3gp", "mov", "flv", "wmv", "ts"
    ));

    private final List<String> playlist = new ArrayList<>();
    private int currentIndex = 0;

    private MediaPlayer audioPlayer;
    private VideoView videoView;
    private boolean isVideo = false;
    private boolean isUserSeeking = false;

    private TextView tvTitle;
    private TextView tvSubtitle;
    private FrameLayout layoutMediaViewport;
    private LinearLayout layoutAudioView;
    private ImageView ivAudioArt;
    private TextView tvAudioArtistAlbum;

    private TextView tvCurrentTime;
    private TextView tvTotalDuration;
    private SeekBar seekBar;

    private ImageButton btnPrevious;
    private ImageButton btnRewind;
    private ImageButton btnPlayPause;
    private ImageButton btnFastForward;
    private ImageButton btnNext;
    private ImageButton btnBack;
    private ImageButton btnShare;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            progressHandler.postDelayed(this, 300);
        }
    };

    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                pauseMedia();
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_player);

        initViews();
        setupAudioManager();
        parseIntentData();

        if (playlist.isEmpty()) {
            Toast.makeText(this, R.string.cannot_open_file, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        playMediaAtIndex(currentIndex);
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tv_player_title);
        tvSubtitle = findViewById(R.id.tv_player_subtitle);
        tvTitle.setSelected(true); // Enable marquee scrolling

        layoutMediaViewport = findViewById(R.id.layout_media_viewport);
        videoView = findViewById(R.id.video_view);
        layoutAudioView = findViewById(R.id.layout_audio_view);
        ivAudioArt = findViewById(R.id.iv_audio_art);
        tvAudioArtistAlbum = findViewById(R.id.tv_audio_artist_album);

        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalDuration = findViewById(R.id.tv_total_duration);
        seekBar = findViewById(R.id.seek_bar_player);

        btnPrevious = findViewById(R.id.btn_player_previous);
        btnRewind = findViewById(R.id.btn_player_rewind);
        btnPlayPause = findViewById(R.id.btn_player_play_pause);
        btnFastForward = findViewById(R.id.btn_player_fast_forward);
        btnNext = findViewById(R.id.btn_player_next);
        btnBack = findViewById(R.id.btn_player_back);
        btnShare = findViewById(R.id.btn_player_share);

        btnBack.setOnClickListener(v -> finish());
        btnShare.setOnClickListener(v -> shareCurrentFile());

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnRewind.setOnClickListener(v -> rewind());
        btnFastForward.setOnClickListener(v -> fastForward());
        btnPrevious.setOnClickListener(v -> playPrevious());
        btnNext.setOnClickListener(v -> playNext());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                isUserSeeking = false;
                seekTo(sb.getProgress());
            }
        });
    }

    private void setupAudioManager() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    private void parseIntentData() {
        Intent intent = getIntent();
        if (intent == null) return;

        String currentPath = intent.getStringExtra(EXTRA_CURRENT_PATH);
        ArrayList<String> paths = intent.getStringArrayListExtra(EXTRA_PLAYLIST_PATHS);
        int startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0);

        if (paths != null && !paths.isEmpty()) {
            playlist.addAll(paths);
            currentIndex = Math.max(0, Math.min(startIndex, playlist.size() - 1));
        } else if (currentPath != null && !currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            boolean isTargetAudio = isAudioFile(currentFile);
            File parentDir = currentFile.getParentFile();
            if (parentDir != null && parentDir.exists()) {
                File[] siblings = parentDir.listFiles();
                if (siblings != null) {
                    Arrays.sort(siblings, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    for (File f : siblings) {
                        if (!f.isDirectory() && (isTargetAudio ? isAudioFile(f) : isVideoFile(f))) {
                            playlist.add(f.getAbsolutePath());
                        }
                    }
                }
            }
            if (playlist.isEmpty()) {
                playlist.add(currentFile.getAbsolutePath());
            }
            for (int i = 0; i < playlist.size(); i++) {
                if (playlist.get(i).equalsIgnoreCase(currentFile.getAbsolutePath())) {
                    currentIndex = i;
                    break;
                }
            }
            if (currentIndex < 0) currentIndex = 0;
        }
    }

    private void playMediaAtIndex(int index) {
        if (index < 0 || index >= playlist.size()) return;
        currentIndex = index;

        stopCurrentMedia();

        String path = playlist.get(currentIndex);
        File file = new File(path);
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.cannot_play_media, file.getName()), Toast.LENGTH_SHORT).show();
            return;
        }

        isVideo = isVideoFile(file);
        tvTitle.setText(file.getName());
        tvSubtitle.setText(getString(R.string.media_position_format, String.valueOf(currentIndex + 1), String.valueOf(playlist.size())));

        btnPrevious.setEnabled(true);
        btnPrevious.setAlpha(1.0f);
        btnNext.setEnabled(true);
        btnNext.setAlpha(1.0f);

        requestAudioFocus();

        if (isVideo) {
            setupVideoPlayback(file);
        } else {
            setupAudioPlayback(file);
        }

        updatePlayPauseButton(true);
        tvTitle.announceForAccessibility(getString(R.string.media_playing, file.getName()));
        progressHandler.post(progressRunnable);
    }

    private void setupVideoPlayback(File file) {
        videoView.setVisibility(View.VISIBLE);
        layoutAudioView.setVisibility(View.GONE);

        videoView.setVideoPath(file.getAbsolutePath());
        videoView.setOnPreparedListener(mp -> {
            int duration = videoView.getDuration();
            if (duration > 0) {
                seekBar.setMax(duration);
                tvTotalDuration.setText(formatTime(duration));
            }
            videoView.start();
            updatePlayPauseButton(true);
        });

        videoView.setOnCompletionListener(mp -> onMediaCompleted());

        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, getString(R.string.cannot_play_media, file.getName()), Toast.LENGTH_SHORT).show();
            updatePlayPauseButton(false);
            return true;
        });
    }

    private void setupAudioPlayback(File file) {
        videoView.setVisibility(View.GONE);
        layoutAudioView.setVisibility(View.VISIBLE);

        loadAudioMetadata(file);

        try {
            audioPlayer = new MediaPlayer();
            audioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            audioPlayer.setDataSource(file.getAbsolutePath());
            audioPlayer.prepare();
            int duration = audioPlayer.getDuration();
            if (duration > 0) {
                seekBar.setMax(duration);
                tvTotalDuration.setText(formatTime(duration));
            }
            audioPlayer.start();
            updatePlayPauseButton(true);

            audioPlayer.setOnCompletionListener(mp -> onMediaCompleted());

            audioPlayer.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, getString(R.string.cannot_play_media, file.getName()), Toast.LENGTH_SHORT).show();
                updatePlayPauseButton(false);
                return true;
            });
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.cannot_play_media, e.getMessage()), Toast.LENGTH_SHORT).show();
            updatePlayPauseButton(false);
        }
    }

    private void loadAudioMetadata(File file) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(file.getAbsolutePath());
            String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);

            StringBuilder sb = new StringBuilder();
            if (artist != null && !artist.trim().isEmpty()) {
                sb.append(artist);
            }
            if (album != null && !album.trim().isEmpty()) {
                if (sb.length() > 0) sb.append(" \u2022 ");
                sb.append(album);
            }
            if (sb.length() > 0) {
                tvAudioArtistAlbum.setText(sb.toString());
                tvAudioArtistAlbum.setVisibility(View.VISIBLE);
            } else {
                tvAudioArtistAlbum.setText(file.getParentFile() != null ? file.getParentFile().getName() : "");
                tvAudioArtistAlbum.setVisibility(View.VISIBLE);
            }
        } catch (Exception ignored) {
            tvAudioArtistAlbum.setVisibility(View.GONE);
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
    }

    private void togglePlayPause() {
        if (isVideo) {
            try {
                if (videoView.isPlaying()) {
                    videoView.pause();
                    updatePlayPauseButton(false);
                } else {
                    requestAudioFocus();
                    videoView.start();
                    updatePlayPauseButton(true);
                }
            } catch (Exception e) {
                playMediaAtIndex(currentIndex);
            }
        } else {
            if (audioPlayer != null) {
                try {
                    if (audioPlayer.isPlaying()) {
                        audioPlayer.pause();
                        updatePlayPauseButton(false);
                    } else {
                        requestAudioFocus();
                        audioPlayer.start();
                        updatePlayPauseButton(true);
                        progressHandler.removeCallbacks(progressRunnable);
                        progressHandler.post(progressRunnable);
                    }
                } catch (Exception e) {
                    playMediaAtIndex(currentIndex);
                }
            } else {
                playMediaAtIndex(currentIndex);
            }
        }
    }

    private void pauseMedia() {
        if (isVideo && videoView.isPlaying()) {
            videoView.pause();
            updatePlayPauseButton(false);
        } else if (audioPlayer != null && audioPlayer.isPlaying()) {
            audioPlayer.pause();
            updatePlayPauseButton(false);
        }
    }

    private void rewind() {
        int current = getCurrentPosition();
        int target = Math.max(0, current - SEEK_STEP_MS);
        seekTo(target);
        String announcement = getString(R.string.btn_rewind_10s) + ", " + formatTime(target);
        btnRewind.announceForAccessibility(announcement);
    }

    private void fastForward() {
        int current = getCurrentPosition();
        int duration = getDuration();
        int target = Math.min(duration, current + SEEK_STEP_MS);
        seekTo(target);
        String announcement = getString(R.string.btn_forward_10s) + ", " + formatTime(target);
        btnFastForward.announceForAccessibility(announcement);
    }

    private void seekTo(int msec) {
        if (isVideo) {
            videoView.seekTo(msec);
        } else if (audioPlayer != null) {
            audioPlayer.seekTo(msec);
        }
        tvCurrentTime.setText(formatTime(msec));
        seekBar.setProgress(msec);
    }

    private int getCurrentPosition() {
        if (isVideo) {
            return videoView.getCurrentPosition();
        } else if (audioPlayer != null) {
            try { return audioPlayer.getCurrentPosition(); } catch (Exception ignored) {}
        }
        return 0;
    }

    private int getDuration() {
        if (isVideo) {
            return videoView.getDuration();
        } else if (audioPlayer != null) {
            try { return audioPlayer.getDuration(); } catch (Exception ignored) {}
        }
        return 0;
    }

    private void updateProgress() {
        if (isUserSeeking) return;
        int current = getCurrentPosition();
        int duration = getDuration();

        if (duration > 0 && seekBar.getMax() != duration) {
            seekBar.setMax(duration);
            tvTotalDuration.setText(formatTime(duration));
        }
        seekBar.setProgress(current);
        tvCurrentTime.setText(formatTime(current));
    }

    private void playPrevious() {
        if (playlist.isEmpty()) return;
        // If current track has progressed more than 3 seconds, restart it
        if (getCurrentPosition() > 3000) {
            seekTo(0);
            if (audioPlayer != null && !audioPlayer.isPlaying()) {
                requestAudioFocus();
                audioPlayer.start();
                updatePlayPauseButton(true);
            } else if (isVideo && !videoView.isPlaying()) {
                requestAudioFocus();
                videoView.start();
                updatePlayPauseButton(true);
            }
            return;
        }

        if (playlist.size() > 1) {
            int prevIndex = currentIndex - 1;
            if (prevIndex < 0) {
                prevIndex = playlist.size() - 1; // Wrap around to end
            }
            playMediaAtIndex(prevIndex);
        } else {
            seekTo(0);
        }
    }

    private void playNext() {
        if (playlist.isEmpty()) return;
        if (playlist.size() > 1) {
            int nextIndex = currentIndex + 1;
            if (nextIndex >= playlist.size()) {
                nextIndex = 0; // Wrap around to start
            }
            playMediaAtIndex(nextIndex);
        } else {
            seekTo(0);
        }
    }

    private void onMediaCompleted() {
        if (playlist.size() > 1) {
            if (currentIndex < playlist.size() - 1) {
                playMediaAtIndex(currentIndex + 1);
            } else {
                updatePlayPauseButton(false);
                seekTo(0);
            }
        } else {
            updatePlayPauseButton(false);
            seekTo(0);
        }
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        if (isPlaying) {
            btnPlayPause.setImageResource(R.drawable.ic_pause);
            btnPlayPause.setContentDescription(getString(R.string.btn_pause));
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_action_play);
            btnPlayPause.setContentDescription(getString(R.string.btn_play));
        }
    }

    private void stopCurrentMedia() {
        progressHandler.removeCallbacks(progressRunnable);
        if (videoView != null) {
            try { videoView.stopPlayback(); } catch (Exception ignored) {}
        }
        if (audioPlayer != null) {
            try {
                if (audioPlayer.isPlaying()) audioPlayer.stop();
                audioPlayer.release();
            } catch (Exception ignored) {}
            audioPlayer = null;
        }
    }

    private void requestAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build())
                        .setAcceptsDelayedFocusGain(true)
                        .setWillPauseWhenDucked(false)
                        .setOnAudioFocusChangeListener(focusChange -> {
                            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                                pauseMedia();
                            }
                        })
                        .build();
            }
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(focusChange -> {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    pauseMedia();
                }
            }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
    }

    private void shareCurrentFile() {
        if (currentIndex < 0 || currentIndex >= playlist.size()) return;
        File file = new File(playlist.get(currentIndex));
        if (!file.exists()) return;

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            String ext = getExtension(file.getName());
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime == null) mime = isVideo ? "video/*" : "audio/*";

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mime);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share) + " " + file.getName()));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String formatTime(int millis) {
        int seconds = (millis / 1000) % 60;
        int minutes = (millis / (1000 * 60)) % 60;
        int hours = millis / (1000 * 60 * 60);
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    public static boolean isAudioFile(File file) {
        if (file == null || file.isDirectory()) return false;
        String ext = getExtension(file.getName());
        return AUDIO_EXTENSIONS.contains(ext);
    }

    public static boolean isVideoFile(File file) {
        if (file == null || file.isDirectory()) return false;
        String ext = getExtension(file.getName());
        return VIDEO_EXTENSIONS.contains(ext);
    }

    private static String getExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(dot + 1).toLowerCase(Locale.ENGLISH);
        }
        return "";
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause playback if exiting or screen off
        pauseMedia();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCurrentMedia();
        abandonAudioFocus();
        try {
            unregisterReceiver(noisyReceiver);
        } catch (Exception ignored) {}
    }
}
