package mrm.com.emailshareto;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;


public class HandleShareActivity extends ListActivity {
    private final static String[][] targets = {
            new String[]{"miglena2s@yahoo.com"},
            new String[]{"liahim@yahoo.com", "liaxim@gmail.com"},
            new String[]{"ratchoivanov@gmail.com", "ivankamihail@gmail.com"},
            new String[]{"tonisemova@yahoo.com"}};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();

        final String[] arrayAdapterData = new String[targets.length];
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < targets.length; ++i) {
            sb.setLength(0);
            for (String s : targets[i]) {
                sb.append(s);
                sb.append("; ");
            }

            arrayAdapterData[i] = sb.toString();
        }

        setListAdapter(new ArrayAdapter(this, R.layout.targets_listview_item, arrayAdapterData));
    }

    protected void onListItemClick(ListView l, View v, int position, long id) {
        final Intent intent = getIntent();

        intent.setClassName("com.google.android.gm", "com.google.android.gm.ComposeActivityGmail");
        intent.putExtra(Intent.EXTRA_EMAIL, targets[position]);
        startActivity(intent);
        finish();
    }
}
