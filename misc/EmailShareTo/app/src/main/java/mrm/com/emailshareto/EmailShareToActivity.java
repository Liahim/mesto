package mrm.com.emailshareto;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;

import java.util.HashSet;
import java.util.Set;


public class EmailShareToActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        final String[][] targets = {
                new String[]{"miglena2s@yahoo.com"},
                new String[]{"liahim@yahoo.com", "liaxim@gmail.com"},
                new String[]{"ratchoivanov@gmail.com", "ivankamihail@gmail.com"},
                new String[]{"tonisemova@yahoo.com"}};

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

        if ((Intent.ACTION_SEND_MULTIPLE.equals(action) || Intent.ACTION_SEND.equals(action)) && type != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setAdapter(new ArrayAdapter(EmailShareToActivity.this, R.layout.targets_listview_item, arrayAdapterData), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    intent.setClassName("com.google.android.gm", "com.google.android.gm.ComposeActivityGmail");
                    intent.putExtra(Intent.EXTRA_EMAIL, targets[which]);
                    startActivity(intent);
                    finish();
                }
            });
            builder.show();
        } else {
            setContentView(R.layout.activity_email_share_to);

            findViewById(R.id.rootView).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(EmailShareToActivity.this);
                    final View view = getLayoutInflater().inflate(R.layout.add_target, null);
                    view.findViewById(R.id.button_add).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View v) {
                            SharedPreferences prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE);
                            Set<String> targets = prefs.getStringSet("targets", null);
                            if (null == targets) {
                                targets = new HashSet<String>();
                            }

                            final String prefix;
                            if (((RadioButton) view.findViewById(R.id.radioButtonTo)).isChecked()) {
                                prefix = "T";
                            } else {
                                prefix = "B";
                            }
                            String text = ((EditText) view.findViewById(R.id.editTextEmail)).getText().toString();

                            targets.add(prefix + text);
                            prefs.edit().putStringSet("targets", targets).apply();
                        }
                    });
                    builder.setView(view);
                    final Dialog dlg = builder.create();
                    dlg.setCanceledOnTouchOutside(true);
                    dlg.show();
                }
            });
        }


//        Intent gmail = new Intent(Intent.ACTION_VIEW);
//        gmail.setClassName("com.google.android.gm","com.google.android.gm.ComposeActivityGmail");
//        gmail.putExtra(Intent.EXTRA_EMAIL, new String[] { "jckdsilva@gmail.com" });
//        gmail.setData(Uri.parse("liaxim@gmail.com"));
//        gmail.putExtra(Intent.EXTRA_SUBJECT, "enter something");
//        gmail.setType("plain/text");
//        gmail.putExtra(Intent.EXTRA_TEXT, "hi android jack!");
//        startActivity(gmail);
    }

    private void handleSendText(final Intent intent) {

    }

    private void handleSendImage(final Intent intent) {

    }

    private void handleSendMultipleImages(final Intent intent) {

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.email_share_to, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
