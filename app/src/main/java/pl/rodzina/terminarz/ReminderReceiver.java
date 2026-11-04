package pl.rodzina.terminarz;
import android.content.*;import android.app.*;import android.os.*;
public class ReminderReceiver extends BroadcastReceiver{
 @Override public void onReceive(Context c,Intent i){String title=i.getStringExtra("title");if(title==null)title="Wydarzenie rodzinne";Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,"family_events"):new Notification.Builder(c);b.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Terminarz rodzinny").setContentText(title).setAutoCancel(true);((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(i.getIntExtra("id",1),b.build());}
}