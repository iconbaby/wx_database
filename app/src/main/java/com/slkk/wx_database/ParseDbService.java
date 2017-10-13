package com.slkk.wx_database;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.util.Log;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by slkk on 2017/10/13.
 */

public class ParseDbService extends Service {
    public static final String WX_ROOT_PATH = "/data/data/com.tencent.mm/";
    public static final String TAG = "ParseDbService";
    public static final String WX_SP_UNI_PATH = WX_ROOT_PATH + "shared_prefs/auth_info_key_prefs.xml";
    private String imie;
    private String uin;
    private String pwd;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        execRootCmd("chmod -R 777 " + WX_ROOT_PATH);
        imie = getImie();
        uin = getUIN();
        pwd = calcPassword();
        getDbFile();
        cpyDB();
        openDB(new File(myDBPath));
        Log.i(TAG, "onCreate: " + imie);
        Log.i(TAG, "onCreate: " + uin);
        return super.onStartCommand(intent, flags, startId);
    }

    private void openDB(File dbfile) {
        Log.i(TAG, "openDB: ");
        Context context = getApplicationContext();
        SQLiteDatabase.loadLibs(context);
        SQLiteDatabaseHook sqliteDatabaseHook = new SQLiteDatabaseHook() {

            @Override
            public void preKey(SQLiteDatabase sqLiteDatabase) {

            }

            @Override
            public void postKey(SQLiteDatabase sqLiteDatabase) {
                sqLiteDatabase.rawExecSQL("PRAGMA cipher_migrate;");
            }
        };
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbfile, pwd, null, sqliteDatabaseHook);
        Cursor c1 = db.rawQuery("select * from rcontact where verifyFlag = 0 and type != 4 and type != 2 and nickname != '' limit 20, 9999", null);
        while (c1.moveToNext()) {
            String userName = c1.getString(c1.getColumnIndex("username"));
            String alias = c1.getString(c1.getColumnIndex("alias"));
            String nickName = c1.getString(c1.getColumnIndex("nickname"));
            Log.i(TAG, "openDB: " + "userName:" + userName + "+++" + "alias:" + alias + "+++" + "nickName:" + nickName);
        }
        c1.close();
        db.close();
    }


    private void execRootCmd(String s) {
        try {
            Process localProcess = Runtime.getRuntime().exec("adb shell");
            Object localObject = localProcess.getOutputStream();
            DataOutputStream localDataOutputStream = new DataOutputStream((OutputStream) localObject);
            String str = String.valueOf(s);
            localObject = str + "\n";
            localDataOutputStream.writeBytes((String) localObject);
            localDataOutputStream.flush();
            localDataOutputStream.writeBytes("exit\n");
            localDataOutputStream.flush();
            localProcess.waitFor();
            localObject = localProcess.exitValue();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private String getImie() {
        TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(TELEPHONY_SERVICE);
        String deviceId = tm.getDeviceId();
        return deviceId;
    }

    private String getUIN() {
        File file = new File(WX_SP_UNI_PATH);
        String uin = null;
        try {
            FileInputStream fis = new FileInputStream(file);
            SAXReader saxreader = new SAXReader();
            Document document = saxreader.read(fis);
            Element rootElement = document.getRootElement();
            List<Element> elements = rootElement.elements();
            for (Element e : elements) {
                if ("_auth_uin".equals(e.attributeValue("name"))) {
                    uin = e.attributeValue("value");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return uin;
    }

    private String calcPassword() {
        String md5 = md5(imie + uin);
        String passward = md5.substring(0, 7).toLowerCase();
        return passward;
    }

    private String md5(String source) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(source.getBytes("UTF-8"));
            byte[] digest = md5.digest();
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < digest.length; i++) {
                if (Integer.toHexString(0xff & digest[i]).length() == 1) {
                    sb.append("0").append(Integer.toHexString(0xff & digest[i]));
                } else {
                    sb.append(Integer.toHexString(0xff & digest[i]));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static final String WX_DB_PATH = WX_ROOT_PATH + "MicroMsg";
    private static final String WX_DB_FILE_NAME = "EnMicroMsg.db";
    private List<File> db_files = new ArrayList<File>();

    private void searchFile(File file, String fileName) {
        Log.i(TAG, "searchFile: ");
        if (file.isDirectory()) {
            File[] files1 = file.listFiles();
            if (files1 != null) {
                for (File fl : files1) {
                    searchFile(fl, fileName);
                }
            }
        } else {
            if (file.getName().equals(fileName)) {
                db_files.add(file);
            }
        }
    }

    private void getDbFile() {
        File file = new File(WX_DB_PATH);
        searchFile(file, WX_DB_FILE_NAME);
    }

    private void copyFile(String oldPath, String newPath) {
        int readCont = 0;
        File oldFile = new File(oldPath);
        try {
            FileInputStream fis = new FileInputStream(oldFile);
            FileOutputStream fos = new FileOutputStream(newPath);
            byte[] buffer = new byte[1024 * 2];
            while ((readCont = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, readCont);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String myDBPath = null;

    private void cpyDB() {
        String oldpath = "/data/data/" + getApplicationContext().getPackageName() + "/";
        myDBPath = oldpath + "wx_data.db";
        File db = new File(myDBPath);
        if (db.exists()) {
            return;
        }

        if (db_files.size() != 0) {
            Log.i(TAG, "cpyDB: " + db_files.size());
            for (File fl : db_files) {
                copyFile(fl.getAbsolutePath(), myDBPath);
            }
        }
    }
}
