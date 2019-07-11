package id.net.gmedia.semargres2019;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.leonardus.irfan.ApiVolleyManager;
import com.leonardus.irfan.AppRequestCallback;
import com.leonardus.irfan.JSONBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

import id.net.gmedia.semargres2019.Util.AppSharedPreferences;
import id.net.gmedia.semargres2019.Util.Constant;

public class LoginActivity extends AppCompatActivity {
    //Label activity
    private static final int RC_SIGN_IN = 234;

    //Variabel autentifikasi
    private GoogleSignInClient client;
    private FirebaseAuth auth;
    private CallbackManager callbackManager;

    //Variabel UI
    private Button btn_facebook, btn_gmail;
    private ProgressBar bar_loading;

    private String fcm_id = "";
    private String email = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        //Inisialisasi UI
        btn_facebook = findViewById(R.id.btn_login_facebook);
        btn_gmail = findViewById(R.id.btn_login_gmail);
        bar_loading = findViewById(R.id.bar_loading);

        //inisialisasi Autentifikasi Firebase
        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();

        //Inisialisasi Login Google
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                //.requestIdToken("356805825965-4lpaqftajn2llvl9p1bo8ns83g62a0l7.apps.googleusercontent.com")
                .requestEmail().build();
        client = GoogleSignIn.getClient(this, gso);

        //Inisialisasi Login Facebook
        callbackManager = CallbackManager.Factory.create();
        LoginManager loginManager = LoginManager.getInstance();
        loginManager.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener
                        ( LoginActivity.this,  new OnSuccessListener<InstanceIdResult>() {
                    @Override
                    public void onSuccess(InstanceIdResult instanceIdResult) {
                        fcm_id = instanceIdResult.getToken();
                        Log.d(Constant.TAG, fcm_id);
                    }
                });

                firebaseAuthWithFacebook(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {
                setLoading(false);
                Log.w(Constant.TAG, "Login dibatalkan");
            }

            @Override
            public void onError(FacebookException error) {
                setLoading(false);
                Toast.makeText(getApplicationContext(),"Autentikasi Facebook Gagal",Toast.LENGTH_SHORT).show();
                Log.e(Constant.TAG, error.getMessage());
            }
        });

        btn_facebook.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLoading(true);
                LoginManager.getInstance().logInWithReadPermissions
                        (LoginActivity.this, Arrays.asList("email", "public_profile"));
            }
        });

        btn_gmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLoading(true);
                Intent signInIntent = client.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
            }
        });
    }

    private void setLoading(boolean loading){
        if(loading){
            bar_loading.setVisibility(View.VISIBLE);
            btn_gmail.setEnabled(false);
            btn_facebook.setEnabled(false);
        }
        else{
            bar_loading.setVisibility(View.INVISIBLE);
            btn_gmail.setEnabled(true);
            btn_facebook.setEnabled(true);
        }
    }

    //Menangani hasil login facebook dan google
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);

        //Apabila kode request adalah kode sign in google yang telah didefinisikan
        if(requestCode == RC_SIGN_IN){
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);

            try{
                //Sign in berhasil, autentifikasi dengan Firebase
                GoogleSignInAccount account = task.getResult(ApiException.class);

                FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener
                        ( this,  new OnSuccessListener<InstanceIdResult>() {
                    @Override
                    public void onSuccess(InstanceIdResult instanceIdResult) {
                        fcm_id = instanceIdResult.getToken();
                        AppSharedPreferences.setFcmId(LoginActivity.this, fcm_id);
                        Log.d(Constant.TAG, fcm_id);
                    }
                });

                if(account != null){
                    firebaseAuthWithGoogle(account);
                }
            }
            catch (ApiException e){
                setLoading(false);
                Log.e(Constant.TAG, e.toString());
                Toast.makeText(LoginActivity.this, "Autentikasi Gagal", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    //Proses Autentifikasi Google
    private void firebaseAuthWithGoogle(GoogleSignInAccount acc){
        Log.d(Constant.TAG, "FirebaseAuthGoogle : " + acc.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(acc.getIdToken(), null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(Constant.TAG, "signInWithCredential:success");
                            client.signOut();

                            loginToApp("Google");
                        } else {
                            // If sign in fails, display a message to the user.
                            setLoading(false);
                            Log.w(Constant.TAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Autentikasi Google gagal",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    //Proses Autentifikasi Facebook
    private void firebaseAuthWithFacebook(AccessToken accessToken){
        AuthCredential credential = FacebookAuthProvider.getCredential(accessToken.getToken());
        auth.signInWithCredential(credential).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    Log.d(Constant.TAG, "signInWithCredential:success");
                    LoginManager.getInstance().logOut();

                    loginToApp("Facebook");
                } else {
                    // If sign in fails, display a message to the user.
                    setLoading(false);
                    Log.w(Constant.TAG, "signInWithCredential:failure", task.getException());
                    Toast.makeText(LoginActivity.this, "Autentikasi Facebook gagal",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loginToApp(String type){
        final JSONBuilder body = new JSONBuilder();
        body.add("uid", FirebaseAuth.getInstance().getUid());
        if(FirebaseAuth.getInstance().getCurrentUser() != null){
            email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            body.add("email", email);
            body.add("profile_name", FirebaseAuth.getInstance().getCurrentUser().getDisplayName());
            body.add("foto", FirebaseAuth.getInstance().getCurrentUser().getPhotoUrl());
        }
        else{
            body.add("email", "");
            body.add("profile_name", "");
            body.add("foto", "");
            Log.w(Constant.TAG, "Current User kosong");
        }

        body.add("type", type);
        body.add("fcm_id", fcm_id);

        ApiVolleyManager.getInstance().addSecureRequest(this, Constant.URL_LOGIN, ApiVolleyManager.METHOD_POST,
                Constant.HEADER_AUTH, body.create(), new AppRequestCallback(new AppRequestCallback.SimpleRequestListener() {
                    @Override
                    public void onSuccess(String response) {
                        try{
                            Log.d(Constant.TAG, response);

                            //Masuk halaman home
                            JSONObject json = new JSONObject(response);
                            int status = json.getInt("status");

                            if(status == 0){
                                //daftar
                                register(body.create());
                            }
                            else{
                                //login
                                setLoading(false);
                                AppSharedPreferences.Login(LoginActivity.this,
                                        json.getString("uid"), json.getString("token"), email);

                                /*Intent i;
                                if(!AppSharedPreferences.getRegistered(LoginActivity.this)){
                                    i = new Intent(LoginActivity.this, RegisterActivity.class);
                                }
                                else{
                                    i = new Intent(LoginActivity.this, MainActivity.class);
                                }*/
                                Intent i = new Intent(LoginActivity.this, MainActivity.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(i);

                                finish();
                            }
                        }
                        catch (JSONException e){
                            setLoading(false);
                            Log.e(Constant.TAG, e.getMessage());
                            Toast.makeText(LoginActivity.this, "Autentikasi gagal", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFail(String message) {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void register(JSONObject body){
        ApiVolleyManager.getInstance().addSecureRequest(this, Constant.URL_REGISTER, ApiVolleyManager.METHOD_POST,
                Constant.HEADER_AUTH, body, new AppRequestCallback(new AppRequestCallback.RequestListener() {
                    @Override
                    public void onEmpty(String message) {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onSuccess(String response) {
                        try{
                            Log.d(Constant.TAG, response);

                            //Masuk halaman home
                            JSONObject json = new JSONObject(response);
                            int status = json.getInt("status");

                            if(status == 1){
                                //daftar
                                AppSharedPreferences.Login(LoginActivity.this, json.
                                        getString("uid"), json.getString("token"), email);
                                Intent i = new Intent(LoginActivity.this, RegisterActivity.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(i);

                                finish();
                            }
                            else{
                                //login
                                Toast.makeText(LoginActivity.this,
                                        json.getString("message"), Toast.LENGTH_SHORT).show();
                            }
                        }
                        catch (JSONException e){
                            Log.e(Constant.TAG, e.getMessage());
                            Toast.makeText(LoginActivity.this,
                                    "Autentikasi gagal", Toast.LENGTH_SHORT).show();
                        }
                        setLoading(false);
                    }

                    @Override
                    public void onFail(String message) {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }));
    }
}