package com.beni.livecallcaptions;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.*;
import android.speech.*;
import android.view.*;
import android.widget.*;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CaptionService extends Service {
    private WindowManager wm;
    private View floatView;
    private TextView text;
    private SpeechRecognizer speech;
    private Translator translator;
    private boolean listening=false, stopped=false;
    private int direction;
    private boolean speakerMode=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final AtomicInteger revision=new AtomicInteger();
    private Intent speechIntent;
    @Override public void onCreate(){super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        direction=intent==null?0:intent.getIntExtra("direction",0);
        speakerMode=intent!=null && intent.getBooleanExtra("speakerMode",false);
        stopped=false;
        createNotification();
        showFloat();
        String source=direction==0?TranslateLanguage.ENGLISH:TranslateLanguage.PERSIAN;
        String target=direction==0?TranslateLanguage.PERSIAN:TranslateLanguage.ENGLISH;
        translator=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build());
        setText("Downloading / preparing translation model...");
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
            .addOnSuccessListener(v->{
                if(stopped)return;
                setText("Ready. Listening for speech...");
                beginSpeech();
            })
            .addOnFailureListener(e->setText("Translation model unavailable: "+e.getMessage()));
        return START_NOT_STICKY;
    }
    private void createNotification(){
        String id="beni_caption_channel";
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=new NotificationChannel(id,"Beni captions",NotificationManager.IMPORTANCE_LOW);manager.createNotificationChannel(channel);}
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,id):new Notification.Builder(this);
        Notification n=builder.setContentTitle("Beni captions").setContentText("Experimental microphone captions active").setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(77,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(77,n);
    }
    private void showFloat(){
        if(floatView!=null)return;
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(18,12,18,12);
        box.setBackgroundColor(0xEE13254B);
        LinearLayout bar=new LinearLayout(this);
        TextView label=new TextView(this);
        label.setText("Beni  •  Captions");
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        bar.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Button close=new Button(this);
        close.setText("×");
        bar.addView(close);
        box.addView(bar);
        text=new TextView(this);
        text.setTextColor(Color.WHITE);
        text.setTextSize(16);
        text.setText("Starting...");
        box.addView(text);
        if(speakerMode){
            TextView hint=new TextView(this);
            hint.setText("\nSPEAKER TEST: switch on speakerphone inside Phone app. Audio access may still be blocked by Android.");
            hint.setTextColor(0xFFFFD580);
            hint.setTextSize(13);
            box.addView(hint);
        }
        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(
            Math.min(getResources().getDisplayMetrics().widthPixels-32,900),
            WindowManager.LayoutParams.WRAP_CONTENT,type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
        params.y=110;
        wm.addView(box,params);
        floatView=box;
        close.setOnClickListener(v->stopSelf());
        label.setOnTouchListener(new View.OnTouchListener(){
            float previousY;
            @Override public boolean onTouch(View v,android.view.MotionEvent event){
                if(event.getAction()==MotionEvent.ACTION_DOWN){previousY=event.getRawY();return true;}
                if(event.getAction()==MotionEvent.ACTION_MOVE){params.y+=(int)(event.getRawY()-previousY);previousY=event.getRawY();wm.updateViewLayout(box,params);return true;}
                return false;
            }
        });
    }
    private void beginSpeech(){
        if(stopped)return;
        if(!SpeechRecognizer.isRecognitionAvailable(this)){setText("No speech recognizer installed on this phone.");return;}
        speechIntent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,direction==0?"en-US":"fa-IR");
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        speech=SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle b){listening=true;}
            public void onBeginningOfSpeech(){}
            public void onRmsChanged(float v){}
            public void onBufferReceived(byte[] b){}
            public void onEndOfSpeech(){}
            public void onError(int err){listening=false;if(!stopped){setText("Listening unavailable or interrupted (code "+err+"). If on a SIM call, Android may block the mic.");restart();}}
            public void onResults(Bundle b){parse(b);listening=false;restart();}
            public void onPartialResults(Bundle b){parse(b);}
            public void onEvent(int t,Bundle b){}
        });
        speech.startListening(speechIntent);
    }
    private void parse(Bundle b){
        if(b==null)return;
        ArrayList<String> items=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(items==null||items.isEmpty())return;
        String original=items.get(0);
        int request=revision.incrementAndGet();
        setText("Original: "+original+"\nTranslating...");
        if(translator!=null)translator.translate(original).addOnSuccessListener(result->{
            if(!stopped && request==revision.get())setText("Original: "+original+"\nTranslation: "+result);
        }).addOnFailureListener(e->setText("Original: "+original+"\nTranslation error: "+e.getMessage()));
    }
    private void restart(){handler.postDelayed(()->{if(!stopped && speech!=null){try{speech.startListening(speechIntent);}catch(Exception e){setText("Restart error: "+e.getMessage());}}},700);}
    private void setText(String value){handler.post(()->{if(text!=null)text.setText(value);});}
    @Override public void onDestroy(){
        stopped=true;revision.incrementAndGet();handler.removeCallbacksAndMessages(null);
        if(speech!=null){speech.cancel();speech.destroy();speech=null;}
        if(translator!=null){translator.close();translator=null;}
        if(floatView!=null){wm.removeView(floatView);floatView=null;}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
