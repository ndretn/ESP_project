package it.unipd.dei.esp1617.patova.hdresp;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

public class InfoActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.info_activity);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Make links in the text view clickable
        TextView thanksView = findViewById(R.id.thanks_text);
        thanksView.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
