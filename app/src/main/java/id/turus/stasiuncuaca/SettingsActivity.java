package id.turus.stasiuncuaca;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class SettingsActivity extends Activity {
    private static final String PREFS = "thingspeak_config";
    private static final String DEFAULT_CHANNEL = "2981880";
    private static final String DEFAULT_READ_KEY = "P4B56Z7HZM56Q7HJ";
    private static final String DEFAULT_AI_KEY = "";
    private android.content.SharedPreferences p;
    private EditText title, channel, readKey, aiKey, aiModel, geminiKey, geminiModel, openRouterKey, openRouterModel, crop, lat, lon, elev;
    private Spinner cult, aiProvider;
    private TextView toggleRead, toggleAi;
    private EditText[] fieldName = new EditText[8];
    private EditText[] fieldUnit = new EditText[8];

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        p = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        title=findViewById(R.id.appTitle); channel=findViewById(R.id.channel); readKey=findViewById(R.id.readKey);
        aiKey=findViewById(R.id.aiKey); aiModel=findViewById(R.id.aiModel); geminiKey=findViewById(R.id.geminiKey); geminiModel=findViewById(R.id.geminiModel); openRouterKey=findViewById(R.id.openRouterKey); openRouterModel=findViewById(R.id.openRouterModel); aiProvider=findViewById(R.id.aiProvider); crop=findViewById(R.id.crop);
        lat=findViewById(R.id.latitude); lon=findViewById(R.id.longitude); elev=findViewById(R.id.elevation);
        cult=findViewById(R.id.cultivation); toggleRead=findViewById(R.id.toggleReadKey); toggleAi=findViewById(R.id.toggleAiKey);
        for(int i=0;i<8;i++){
            int n=i+1;
            fieldName[i]=findViewById(getResources().getIdentifier("fieldName"+n,"id",getPackageName()));
            fieldUnit[i]=findViewById(getResources().getIdentifier("fieldUnit"+n,"id",getPackageName()));
        }
        ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"Konvensional / PHT","Organik"});
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); cult.setAdapter(ad);
        ArrayAdapter<String> aiAd=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"OpenAI","Google Gemini","OpenRouter"});
        aiAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); aiProvider.setAdapter(aiAd);
        load();
        findViewById(R.id.useGps).setOnClickListener(v->startActivity(new android.content.Intent(this,MainActivity.class)));
        findViewById(R.id.saveSettings).setOnClickListener(v->save());
        findViewById(R.id.cancelSettings).setOnClickListener(v->finish());
        toggleRead.setOnClickListener(v->toggle(readKey,toggleRead));
        toggleAi.setOnClickListener(v->toggle(aiKey,toggleAi));
    }

    private void load(){
        title.setText(p.getString("app_title","STASIUN CUACA"));
        String savedChannel=p.getString("channel","").trim();
        String savedReadKey=p.getString("read_key","").trim();
        channel.setText(savedChannel.isEmpty()?DEFAULT_CHANNEL:savedChannel);
        readKey.setText(savedReadKey.isEmpty()?DEFAULT_READ_KEY:savedReadKey);
        String savedAiKey=p.getString("ai_api_key","").trim();
        aiKey.setText(savedAiKey.isEmpty()?DEFAULT_AI_KEY:savedAiKey);
        aiModel.setText(p.getString("ai_model","gpt-6-luna"));
        geminiKey.setText(p.getString("gemini_api_key",""));
        geminiModel.setText(p.getString("gemini_model","gemini-2.5-flash"));
        openRouterKey.setText(p.getString("openrouter_api_key",""));
        openRouterModel.setText(p.getString("openrouter_model","openai/gpt-oss-20b:free"));
        String provider=p.getString("ai_provider","OpenAI");
        aiProvider.setSelection("Google Gemini".equals(provider)?1:("OpenRouter".equals(provider)?2:0));
        crop.setText(p.getString("crop","Tanaman pertanian"));
        lat.setText(f(p.getFloat("latitude",Float.NaN))); lon.setText(f(p.getFloat("longitude",Float.NaN))); elev.setText(f(p.getFloat("elevation",Float.NaN)));
        cult.setSelection(p.getString("farm_cultivation_mode","Konvensional / PHT").toLowerCase(Locale.US).contains("organik")?1:0);
        for(int i=0;i<8;i++){
            fieldName[i].setText(p.getString("field_name_"+(i+1),""));
            fieldUnit[i].setText(p.getString("field_unit_"+(i+1),""));
        }
    }

    private String f(float x){return Float.isFinite(x)?String.format(Locale.US,"%.6f",x):"";}

    private void save(){
        String tv=title.getText().toString().trim(); if(tv.isEmpty())tv="STASIUN CUACA";
        String cv=crop.getText().toString().trim(); if(cv.isEmpty())cv="Tanaman pertanian";
        float la=numOptional(lat.getText().toString()), lo=numOptional(lon.getText().toString()), el=numOptional(elev.getText().toString());
        if(!Float.isNaN(la)&&(la<-90||la>90)){lat.setError("Latitude -90..90");return;}
        if(!Float.isNaN(lo)&&(lo<-180||lo>180)){lon.setError("Longitude -180..180");return;}
        android.content.SharedPreferences.Editor e=p.edit().putString("app_title",tv).putString("channel",channel.getText().toString().trim().isEmpty()?DEFAULT_CHANNEL:channel.getText().toString().trim())
                .putString("read_key",readKey.getText().toString().trim().isEmpty()?DEFAULT_READ_KEY:readKey.getText().toString().trim())
                .putString("ai_provider",aiProvider.getSelectedItem().toString())
                .putString("ai_api_key",aiKey.getText().toString().trim()).putString("ai_model",aiModel.getText().toString().trim().isEmpty()?"gpt-6-luna":aiModel.getText().toString().trim())
                .putString("gemini_api_key",geminiKey.getText().toString().trim()).putString("gemini_model",geminiModel.getText().toString().trim().isEmpty()?"gemini-2.5-flash":geminiModel.getText().toString().trim())
                .putString("openrouter_api_key",openRouterKey.getText().toString().trim()).putString("openrouter_model",openRouterModel.getText().toString().trim().isEmpty()?"openai/gpt-oss-20b:free":openRouterModel.getText().toString().trim())
                .putString("crop",cv).putString("farm_cultivation_mode",cult.getSelectedItem().toString());
        if(!Float.isNaN(la))e.putFloat("latitude",la); else e.remove("latitude");
        if(!Float.isNaN(lo))e.putFloat("longitude",lo); else e.remove("longitude");
        if(!Float.isNaN(el))e.putFloat("elevation",el); else e.remove("elevation");
        for(int i=0;i<8;i++){
            e.putString("field_name_"+(i+1),fieldName[i].getText().toString().trim());
            e.putString("field_unit_"+(i+1),fieldUnit[i].getText().toString().trim());
        }
        e.apply(); Toast.makeText(this,"Pengaturan tersimpan.",Toast.LENGTH_SHORT).show(); finish();
    }

    private float numOptional(String s){try{String t=s==null?"":s.trim();return t.isEmpty()?Float.NaN:Float.parseFloat(t.replace(',','.'));}catch(Exception ex){return Float.NaN;}}
    private void toggle(EditText e,TextView t){boolean visible=e.getInputType()==(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);e.setInputType(InputType.TYPE_CLASS_TEXT|(visible?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));t.setText(visible?"TAMPILKAN":"SEMBUNYIKAN");e.setSelection(e.length());}
}
