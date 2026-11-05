package com.beni.livecallcaptions;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.common.model.DownloadConditions;
import java.util.Locale;

public class AccessibleActivity extends Activity {
    private static final String CAPTION_ACTION="com.beni.livecallcaptions.CAPTION_UPDATE";
    private final int primary=0xFF15335B;
    private final int muted=0xFF546477;
    private Spinner directionSpinner;
    private EditText replyInput, phoneInput;
    private TextView incomingOriginal, incomingTranslated, replyTranslated, status;
    private Button speakButton, translateButton;
    private Translator replyTranslator;
    private TextToSpeech tts;
    private boolean voiceReady=false, captionStarted=false, modelReady=false, registered=false;
    private int direction=0, revision=0;
    private String translatedReply="";
    private final BroadcastReceiver captions=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            String original=intent.getStringExtra("original");
            String translation=intent.getStringExtra("translation");
            if(original!=null&&!original.isEmpty()) incomingOriginal.setText(original);
            if(translation!=null&&!translation.isEmpty()) incomingTranslated.setText(translation);
        }
    };

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(24),dp(18),dp(35));
        root.setBackgroundColor(0xFFF4F7FB);
        scroll.addView(root);
        setContentView(scroll);
        TextView title=label("Beni Accessible Call",27,primary,true);
        root.addView(title);
        addText(root,"Live captions • Translate • Type • Speak",15,muted,false);
        addText(root,"نسخه آزمایشی: متن گفتار و پاسخ صوتی روی همین گوشی. در تماس سیم‌کارت، دریافت صدای طرف مقابل و انتقال صدای مصنوعی به تماس تضمین نمی‌شود.",15,0xFF8D3418,true);
        status=label("در حال آماده‌سازی...",15,primary,true);
        root.addView(status);
        addText(root,"زبان گفتار دریافتی و پاسخ",17,primary,true);
        directionSpinner=new Spinner(this);
        directionSpinner.setMinimumHeight(dp(48));
        String[] directions={"English → فارسی  |  جواب فارسی → English","فارسی → English  |  جواب English → فارسی"};
        directionSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,directions));
        root.addView(directionSpinner);
        directionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id){
                direction=position;
                incomingOriginal.setText("...");
                incomingTranslated.setText("...");
                translatedReply="";
                replyTranslated.setText("...");
                if(speakButton!=null)speakButton.setEnabled(false);
                if(replyInput!=null)replyInput.setHint(direction==0?"پاسخ خود را فارسی بنویسید":"Type your reply in English");
                prepareTranslator();
                if(captionStarted){stopCaptions();startCaptions();}
            }
            @Override public void onNothingSelected(AdapterView<?> p){}
        });

        addText(root,"۱. صحبت‌های طرف مقابل | Incoming speech",20,primary,true);
        incomingOriginal=label("متن اصلی در اینجا نمایش داده می‌شود",20,0xFF26384B,false);
        root.addView(panel(incomingOriginal));
        incomingTranslated=label("ترجمه متن در اینجا نمایش داده می‌شود",22,0xFF0F6651,true);
        root.addView(panel(incomingTranslated));
        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        Button start=makeButton("شروع زیرنویس",0xFF176B57);
        Button stop=makeButton("توقف",0xFF9B3A3A);
        controls.addView(start,new LinearLayout.LayoutParams(0,dp(55),1));
        controls.addView(stop,new LinearLayout.LayoutParams(0,dp(55),1));
        root.addView(controls);
        start.setOnClickListener(v->startCaptions());
        stop.setOnClickListener(v->stopCaptions());

        addText(root,"۲. پاسخ تایپی | Type your answer",20,primary,true);
        replyInput=new EditText(this);
        replyInput.setTextSize(20);
        replyInput.setSingleLine(false);
        replyInput.setMinLines(2);
        replyInput.setHint("پاسخ خود را فارسی بنویسید");
        replyInput.setPadding(dp(12),dp(12),dp(12),dp(12));
        replyInput.setBackground(round(0xFFFFFFFF));
        root.addView(replyInput,new LinearLayout.LayoutParams(-1,-2));
        addText(root,"پاسخ‌های سریع",15,muted,false);
        LinearLayout quick=new LinearLayout(this);
        quick.setOrientation(LinearLayout.VERTICAL);
        root.addView(quick);
        addQuick(quick,"لطفاً آهسته صحبت کنید","Please speak slowly");
        addQuick(quick,"لطفاً دوباره تکرار کنید","Could you repeat that?");
        addQuick(quick,"من از متن برای ارتباط استفاده می‌کنم","I am using captions to communicate");

        translateButton=makeButton("ترجمه پاسخ | Translate",0xFF224D91);
        root.addView(translateButton,new LinearLayout.LayoutParams(-1,dp(55)));
        translateButton.setOnClickListener(v->translateReply(false));
        replyTranslated=label("ترجمه پاسخ در اینجا نمایش داده می‌شود",22,0xFF14586F,true);
        root.addView(panel(replyTranslated));
        speakButton=makeButton("🔊 خواندن پاسخ برای طرف مقابل | Speak",0xFF176B57);
        speakButton.setEnabled(false);
        root.addView(speakButton,new LinearLayout.LayoutParams(-1,dp(60)));
        speakButton.setOnClickListener(v->speakReply());
        Button copy=makeButton("کپی پاسخ ترجمه‌شده",0xFF63738C);
        root.addView(copy,new LinearLayout.LayoutParams(-1,dp(48)));
        copy.setOnClickListener(v->{
            if(translatedReply.isEmpty())return;
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Beni reply",translatedReply));
            showStatus("پاسخ ترجمه‌شده کپی شد.");
        });

        addText(root,"۳. تماس تلفنی آزمایشی (SIM)",18,primary,true);
        phoneInput=new EditText(this);
        phoneInput.setHint("Phone number");
        phoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        root.addView(phoneInput);
        Button dial=makeButton("باز کردن شماره‌گیر گوشی",0xFF495E82);
        root.addView(dial,new LinearLayout.LayoutParams(-1,dp(52)));
        dial.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",phoneInput.getText().toString(),""))));
        addText(root,"مهم: این برنامه صدای مصنوعی را داخل تماس عادی سیم‌کارت تزریق نمی‌کند. برای تماس اینترنتی Beni به Beni یا تماس با هر شماره، نسخه شبکه‌ای جداگانه لازم است.",14,0xFF8D3418,true);
        addText(root,"حریم خصوصی: متن فقط در حافظه موقت نمایش داده می‌شود و ذخیره نمی‌شود. تشخیص گفتار ممکن است به سرویس ابری گوشی متکی باشد. برای استفاده، رضایت طرف مقابل را بگیرید.",13,muted,false);

        tts=new TextToSpeech(this, init->{
            voiceReady=init==TextToSpeech.SUCCESS;
            if(voiceReady)tts.setSpeechRate(0.92f);
            runOnUiThread(()->showStatus(voiceReady?"آماده: پاسخ را تایپ و ترجمه کنید.":"موتور صدای گوشی پیدا نشد. متن و ترجمه همچنان قابل استفاده است."));
        });
        prepareTranslator();
    }
    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value+0.5f);}
    private GradientDrawable round(int color){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(12)); return d;
    }
    private TextView label(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(dp(4),dp(8),dp(4),dp(8));
        if(bold)t.setTypeface(null,Typeface.BOLD);
        return t;
    }
    private void addText(LinearLayout root,String s,int size,int color,boolean bold){root.addView(label(s,size,color,bold));}
    private LinearLayout panel(View content){
        LinearLayout wrap=new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(round(Color.WHITE));
        wrap.setPadding(dp(10),dp(8),dp(10),dp(8));
        wrap.addView(content);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(8);
        wrap.setLayoutParams(p);
        return wrap;
    }
    private Button makeButton(String text,int color){
        Button b=new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(16); b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        b.setMinHeight(dp(48)); return b;
    }
    private void addQuick(LinearLayout root,String fa,String en){
        Button b=makeButton(direction==0?fa:en,0xFF5D779A);
        root.addView(b);
        b.setOnClickListener(v->{replyInput.setText(direction==0?fa:en);});
        // Direction can change later: refresh label at click.
        b.setTag(new String[]{fa,en});
        quickButtons.add(b);
    }
    private final java.util.List<Button> quickButtons=new java.util.ArrayList<>();
    private void refreshQuick(){
        for(Button b:quickButtons){String[] opts=(String[])b.getTag();b.setText(direction==0?opts[0]:opts[1]);}
    }
    private void prepareTranslator(){
        if(replyTranslated==null||directionSpinner==null)return;
        revision++;
        int localRevision=revision;
        modelReady=false;
        translateButton.setEnabled(false);
        if(speakButton!=null)speakButton.setEnabled(false);
        if(replyTranslator!=null){replyTranslator.close();replyTranslator=null;}
        String from=direction==0?TranslateLanguage.PERSIAN:TranslateLanguage.ENGLISH;
        String to=direction==0?TranslateLanguage.ENGLISH:TranslateLanguage.PERSIAN;
        replyTranslator=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build());
        showStatus("در حال آماده‌کردن مدل ترجمه پاسخ (بار اول نیاز به اینترنت دارد)...");
        replyTranslator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
            .addOnSuccessListener(v->{
                if(localRevision!=revision)return;
                modelReady=true;
                translateButton.setEnabled(true);
                refreshQuick();
                showStatus("مدل ترجمه آماده است. پاسخ را تایپ کنید.");
            })
            .addOnFailureListener(e->showStatus("دانلود مدل ترجمه انجام نشد: "+e.getMessage()));
    }
    private void showStatus(String s){if(status!=null)status.setText(s);}
    private void translateReply(boolean speakAfter){
        String text=replyInput.getText().toString().trim();
        if(text.isEmpty()){showStatus("اول پاسخ را تایپ کنید.");return;}
        if(!modelReady||replyTranslator==null){showStatus("مدل ترجمه هنوز آماده نیست.");return;}
        final int r=revision;
        replyTranslated.setText("در حال ترجمه...");
        translateButton.setEnabled(false);
        replyTranslator.translate(text).addOnSuccessListener(result->{
            if(r!=revision)return;
            translatedReply=result;
            replyTranslated.setText(result);
            speakButton.setEnabled(true);
            translateButton.setEnabled(true);
            showStatus("پاسخ آماده است؛ دکمه خواندن پاسخ را بزنید.");
            if(speakAfter)speakReply();
        }).addOnFailureListener(e->{if(r==revision){translateButton.setEnabled(true);showStatus("خطای ترجمه: "+e.getMessage());}});
    }
    private void speakReply(){
        if(translatedReply.isEmpty()){translateReply(true);return;}
        if(!voiceReady){showStatus("موتور صدا آماده نیست؛ متن را نشان می‌دهم.");return;}
        Locale out=direction==0?Locale.US:new Locale("fa","IR");
        int language=tts.setLanguage(out);
        if(language==TextToSpeech.LANG_MISSING_DATA||language==TextToSpeech.LANG_NOT_SUPPORTED){
            showStatus("صدای این زبان روی گوشی نصب نیست. ترجمه متنی در دسترس است.");
            return;
        }
        final boolean resume=captionStarted;
        if(resume)stopCaptions();
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){runOnUiThread(()->showStatus("در حال پخش پاسخ..."));}
            @Override public void onDone(String id){runOnUiThread(()->{showStatus("پخش تمام شد.");if(resume)startCaptions();});}
            @Override public void onError(String id){runOnUiThread(()->{showStatus("خطا در پخش صدا.");if(resume)startCaptions();});}
        });
        int result=tts.speak(translatedReply,TextToSpeech.QUEUE_FLUSH,null,"beni_reply");
        if(result==TextToSpeech.ERROR)showStatus("گوشی نتوانست صدا را پخش کند.");
    }
    private void startCaptions(){
        if(!Settings.canDrawOverlays(this)){
            new AlertDialog.Builder(this).setMessage("برای نمایش زیرنویس روی تماس، مجوز نمایش روی برنامه‌ها را بدهید.")
                .setPositiveButton("تنظیمات",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))))
                .setNegativeButton("انصراف",null).show();
            return;
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},91);return;
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},92);return;
        }
        Intent svc=new Intent(this,CaptionService.class);
        svc.putExtra("direction",direction);
        svc.putExtra("speakerMode",true);
        try{
            if(Build.VERSION.SDK_INT>=26)startForegroundService(svc);else startService(svc);
            captionStarted=true;
            showStatus("زیرنویس روشن شد. پنجره شناور Beni را ببینید.");
        }catch(Exception e){captionStarted=false;showStatus("اجازه شروع زیرنویس داده نشد: "+e.getMessage());}
    }
    private void stopCaptions(){
        stopService(new Intent(this,CaptionService.class));
        captionStarted=false;
        showStatus("زیرنویس متوقف شد.");
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startCaptions();
        else showStatus("مجوز میکروفون یا اعلان داده نشد.");
    }
    @Override protected void onStart(){
        super.onStart();
        IntentFilter filter=new IntentFilter(CAPTION_ACTION);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(captions,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(captions,filter);
        registered=true;
    }
    @Override protected void onStop(){
        if(registered){unregisterReceiver(captions);registered=false;}
        super.onStop();
    }
    @Override protected void onDestroy(){
        if(replyTranslator!=null)replyTranslator.close();
        if(tts!=null){tts.stop();tts.shutdown();}
        super.onDestroy();
    }
}
