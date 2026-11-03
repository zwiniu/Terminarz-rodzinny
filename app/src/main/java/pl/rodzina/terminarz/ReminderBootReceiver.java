package pl.rodzina.terminarz;
import android.content.*;import android.os.*;
public class ReminderBootReceiver extends BroadcastReceiver{
 @Override public void onReceive(Context c,Intent i){if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())){String j=c.getSharedPreferences("reminder_state",0).getString("json","[]");ReminderScheduler.sync(c,j);}}
}