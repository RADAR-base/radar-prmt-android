package org.radarcns.android;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import org.radarcns.R;
import org.radarcns.empaticaE4.MainActivity;


public class OverviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overview);
    }

    /** Called when the user clicks the Send button */
    /*
    public void sendMessage(View view) {
        Intent intent = new Intent(this, MainActivity.class);
//        EditText editText = (EditText) findViewById(R.id.edit_message);
//        String message = editText.getText().toString();
        intent.putExtra("An extra message", "Started from over");
        startActivity(intent);
    }
    */

}
