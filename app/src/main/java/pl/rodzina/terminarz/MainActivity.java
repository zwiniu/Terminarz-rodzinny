package pl.rodzina.terminarz;

import android.app.*;
import android.os.*;
import android.content.*;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import android.net.Uri;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity {
 private WebView web; private FirebaseAuth auth; private FirebaseFirestore db;
 private ListenerRegistration eventsListener, memberListener, requestListener;
 private String familyId="", eventsJson="[]";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private static final String PREF="terminarz_family_";
 @Override public void onCreate(Bundle b){super.onCreate(b);
  getWindow().setStatusBarColor(android.graphics.Color.rgb(247,248,245));
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  auth=FirebaseAuth.getInstance();db=FirebaseFirestore.getInstance();
  web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
  web.addJavascriptInterface(new Bridge(),"Android");web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){refreshAccount();}});
  setContentView(web);web.loadUrl("file:///android_asset/index.html");
  createChannel();
  if(auth.getCurrentUser()!=null)restoreFamily();
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
   requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},55);
 }
 private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("family_events","Terminarz rodzinny",NotificationManager.IMPORTANCE_DEFAULT);c.setDescription("Przypomnienia o wydarzeniach rodzinnych");getSystemService(NotificationManager.class).createNotificationChannel(c);}}
 private void js(String name,String...args){StringBuilder s=new StringBuilder(name+"(");for(int i=0;i<args.length;i++){if(i>0)s.append(',');s.append(JSONObject.quote(args[i]));}s.append(");");handler.post(()->{if(web!=null)web.evaluateJavascript(s.toString(),null);});}
 private String uid(){return auth.getCurrentUser()==null?"":auth.getCurrentUser().getUid();}
 private String prefKey(){return PREF+uid();}
 private void toast(String s){handler.post(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
 private void refreshAccount(){FirebaseUser u=auth.getCurrentUser();js("accountState",u==null?"":u.getEmail(),familyId);}
 private void clearListeners(){if(eventsListener!=null){eventsListener.remove();eventsListener=null;}if(memberListener!=null){memberListener.remove();memberListener=null;}if(requestListener!=null){requestListener.remove();requestListener=null;}}
 private void restoreFamily(){familyId=getPreferences(0).getString(prefKey(),"");refreshAccount();if(!familyId.isEmpty())watchFamily();}
 private boolean credentials(String e,String p){if(e==null||!android.util.Patterns.EMAIL_ADDRESS.matcher(e.trim()).matches()){toast("Wpisz poprawny adres e-mail.");return false;}if(p==null||p.length()<6){toast("Hasło musi mieć co najmniej 6 znaków.");return false;}return true;}
 private String authError(Exception e){if(e instanceof FirebaseAuthUserCollisionException)return "Konto z tym adresem już istnieje.";if(e instanceof FirebaseAuthWeakPasswordException)return "Hasło jest za krótkie.";if(e instanceof FirebaseAuthInvalidCredentialsException)return "Nieprawidłowy e-mail lub hasło.";if(e instanceof FirebaseAuthInvalidUserException)return "Nie znaleziono konta.";return e==null?"Nieznany błąd":e.getLocalizedMessage();}
 private void register(String e,String p){if(!credentials(e,p))return;auth.createUserWithEmailAndPassword(e.trim(),p).addOnCompleteListener(this,t->{if(t.isSuccessful()){familyId="";refreshAccount();js("showWorkspace");toast("Konto utworzone. Utwórz rodzinę albo dołącz kodem.");}else toast(authError(t.getException()));});}
 private void login(String e,String p){if(!credentials(e,p))return;auth.signInWithEmailAndPassword(e.trim(),p).addOnCompleteListener(this,t->{if(t.isSuccessful()){restoreFamily();js("showWorkspace");toast("Zalogowano.");}else toast(authError(t.getException()));});}
 private void logout(){clearListeners();getPreferences(0).edit().remove(prefKey()).apply();familyId="";eventsJson="[]";auth.signOut();ReminderScheduler.clear(this);js("accountState","","");js("cloudEvents","[]");}
 private void createFamily(){if(uid().isEmpty()){toast("Najpierw zaloguj się.");return;}String id=UUID.randomUUID().toString().replace("-","").substring(0,24).toUpperCase(Locale.ROOT);
  DocumentReference root=db.collection("families").document(id);Map<String,Object> family=new HashMap<>();family.put("ownerUid",uid());family.put("createdAt",FieldValue.serverTimestamp());family.put("inviteCode",id);
  Map<String,Object> member=new HashMap<>();member.put("email",auth.getCurrentUser().getEmail());member.put("joinedAt",FieldValue.serverTimestamp());
  WriteBatch b=db.batch();b.set(root,family);b.set(root.collection("members").document(uid()),member);
  b.commit().addOnSuccessListener(x->{setFamily(id);toast("Rodzina utworzona. Udostępnij kod zaproszenia.");}).addOnFailureListener(e->toast("Nie udało się utworzyć rodziny: "+e.getMessage()));
 }
 private void joinFamily(String code){if(uid().isEmpty()){toast("Najpierw zaloguj się.");return;}String id=code==null?"":code.trim().toUpperCase(Locale.ROOT);if(!id.matches("[A-F0-9]{24}")){toast("Sprawdź kod zaproszenia.");return;}
  DocumentReference root=db.collection("families").document(id);root.get().addOnSuccessListener(doc->{if(!doc.exists()){toast("Nie znaleziono takiej rodziny.");return;}
   Map<String,Object> req=new HashMap<>();req.put("email",auth.getCurrentUser().getEmail());req.put("requestedAt",FieldValue.serverTimestamp());
   root.collection("requests").document(uid()).set(req).addOnSuccessListener(x->{setFamily(id);toast("Prośba wysłana. Właściciel rodziny musi ją zaakceptować.");}).addOnFailureListener(e->toast("Nie udało się wysłać prośby: "+e.getMessage()));
  }).addOnFailureListener(e->toast("Nie udało się sprawdzić kodu: "+e.getMessage()));
 }
 private void setFamily(String id){familyId=id;getPreferences(0).edit().putString(prefKey(),id).apply();eventsJson="[]";js("accountState",auth.getCurrentUser().getEmail(),id);watchFamily();}
 private void watchFamily(){clearListeners();if(familyId.isEmpty()||uid().isEmpty())return;String fid=familyId;DocumentReference root=db.collection("families").document(fid);
  memberListener=root.collection("members").document(uid()).addSnapshotListener((doc,err)->{if(!fid.equals(familyId))return;if(err!=null){js("cloudNotice","Błąd dostępu do rodziny: "+err.getMessage());return;}
   if(doc==null||!doc.exists()){js("pendingApproval");return;}
   eventsListener=root.collection("data").document("events").addSnapshotListener((data,error)->{if(error!=null){js("cloudNotice","Błąd synchronizacji: "+error.getMessage());return;}
    eventsJson=data!=null&&data.exists()&&data.getString("items")!=null?data.getString("items"):"[]";
    ReminderScheduler.sync(MainActivity.this,eventsJson);js("cloudEvents",eventsJson);
   });
   requestListener=root.collection("requests").addSnapshotListener((snap,error)->{if(error!=null||snap==null)return;JSONArray a=new JSONArray();for(DocumentSnapshot r:snap.getDocuments()){try{JSONObject o=new JSONObject();o.put("uid",r.getId());o.put("email",r.getString("email"));a.put(o);}catch(Exception ignored){}}js("cloudRequests",a.toString());});
   refreshAccount();
  });
 }
 private void approve(String requestUid){if(familyId.isEmpty()||requestUid.isEmpty())return;DocumentReference root=db.collection("families").document(familyId);
  root.get().addOnSuccessListener(f->{if(!uid().equals(f.getString("ownerUid"))){toast("Tylko właściciel może zatwierdzać prośby.");return;}
   DocumentReference req=root.collection("requests").document(requestUid);req.get().addOnSuccessListener(r->{if(!r.exists())return;Map<String,Object> m=new HashMap<>();m.put("email",r.getString("email"));m.put("joinedAt",FieldValue.serverTimestamp());
    WriteBatch b=db.batch();b.set(root.collection("members").document(requestUid),m);b.delete(req);b.commit().addOnSuccessListener(x->toast("Dodano członka rodziny.")).addOnFailureListener(e->toast("Błąd: "+e.getMessage()));
   });
  });
 }
 private void saveEvents(String json){if(familyId.isEmpty()||uid().isEmpty()){toast("Najpierw dołącz do rodziny.");return;}try{JSONArray a=new JSONArray(json);eventsJson=a.toString();Map<String,Object> m=new HashMap<>();m.put("items",eventsJson);m.put("updatedAt",FieldValue.serverTimestamp());
  db.collection("families").document(familyId).collection("data").document("events").set(m).addOnSuccessListener(x->ReminderScheduler.sync(this,eventsJson)).addOnFailureListener(e->js("cloudNotice","Nie udało się zapisać: "+e.getMessage()));
 }catch(Exception e){toast("Nieprawidłowe dane wydarzeń.");}}
 private void resetPassword(String email){if(email==null||!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()){toast("Podaj poprawny adres e-mail.");return;}auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener(t->toast(t.isSuccessful()?"Jeśli konto istnieje, wysłaliśmy link do zmiany hasła.":"Nie udało się wysłać wiadomości."));}
 public class Bridge {
  @JavascriptInterface public void register(String e,String p){runOnUiThread(()->MainActivity.this.register(e,p));}
  @JavascriptInterface public void login(String e,String p){runOnUiThread(()->MainActivity.this.login(e,p));}
  @JavascriptInterface public void logout(){runOnUiThread(()->MainActivity.this.logout());}
  @JavascriptInterface public void createFamily(){runOnUiThread(()->MainActivity.this.createFamily());}
  @JavascriptInterface public void joinFamily(String c){runOnUiThread(()->MainActivity.this.joinFamily(c));}
  @JavascriptInterface public void approve(String id){runOnUiThread(()->MainActivity.this.approve(id));}
  @JavascriptInterface public void saveEvents(String j){runOnUiThread(()->MainActivity.this.saveEvents(j));}
  @JavascriptInterface public void resetPassword(String e){runOnUiThread(()->MainActivity.this.resetPassword(e));}
  @JavascriptInterface public String getEvents(){return eventsJson;}
  @JavascriptInterface public String getEmail(){return auth.getCurrentUser()==null?"":auth.getCurrentUser().getEmail();}
  @JavascriptInterface public String getFamilyCode(){return familyId;}
  @JavascriptInterface public void copy(String s){runOnUiThread(()->{android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("Kod rodziny",s));toast("Skopiowano kod rodziny.");});}
  @JavascriptInterface public void schedule(String id,String title,String when){try{long at=Long.parseLong(when);ReminderScheduler.schedule(MainActivity.this,id,title,at);}catch(Exception ignored){}}
  @JavascriptInterface public void cancel(String id){ReminderScheduler.cancel(MainActivity.this,id);}
 }
 @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
}
