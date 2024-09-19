package com.jok.unshell;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.ArrayMap;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

public class ProxyApplication extends Application {
    private String srcApkPath;
    private String optDirPath;
    private String libDirPath;

    public static void replaceClassLoader1(Context context,DexClassLoader dexClassLoader){
        ClassLoader pathClassLoader = ProxyApplication.class.getClassLoader();
        try {
            // 1.通过currentActivityThread方法获取ActivityThread实例
            Class ActivityThread = pathClassLoader.loadClass("android.app.ActivityThread");
            Method currentActivityThread = ActivityThread.getDeclaredMethod("currentActivityThread");
            Object activityThreadObj = currentActivityThread.invoke(null);
            // 2.拿到mPackagesObj
            Field mPackagesField = ActivityThread.getDeclaredField("mPackages");
            mPackagesField.setAccessible(true);
            ArrayMap mPackagesObj = (ArrayMap) mPackagesField.get(activityThreadObj);
            // 3.拿到LoadedApk
            String packageName = context.getPackageName();
            WeakReference wr = (WeakReference) mPackagesObj.get(packageName);
            Object LoadApkObj = wr.get();
            // 4.拿到mClassLoader
            Class LoadedApkClass = pathClassLoader.loadClass("android.app.LoadedApk");
            Field mClassLoaderField = LoadedApkClass.getDeclaredField("mClassLoader");
            mClassLoaderField.setAccessible(true);
            Object mClassLoader =mClassLoaderField.get(LoadApkObj);
            // 5.将系统组件ClassLoader给替换
            mClassLoaderField.set(LoadApkObj,dexClassLoader);
        }
        catch (Exception e) {
            Log.i("replaceClassLoader1", "error:" + Log.getStackTraceString(e));
            e.printStackTrace();
        }
    }

    private ZipFile getApkZip() throws IOException {
        Log.i("getApkZip", this.getApplicationInfo().sourceDir);
        ZipFile apkZipFile = new ZipFile(this.getApplicationInfo().sourceDir);
        return apkZipFile;
    }

    private byte[] readDexFileFromApk() throws IOException {
        /* 从本体apk中获取dex文件 */
        ZipFile apkZip = this.getApkZip();
        ZipEntry zipEntry = apkZip.getEntry("classes.dex");
        InputStream inputStream = apkZip.getInputStream(zipEntry);
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            baos.write(buffer, 0, length);
        }
        return baos.toByteArray();
    }

    private byte[] splitSrcApkFromDex(byte[] dexFileData) {
        /* 从dex文件中分离源apk文件 */
        int length = dexFileData.length;
        ByteBuffer bb = ByteBuffer.wrap(Arrays.copyOfRange(dexFileData, length - 8, length));
        bb.order(java.nio.ByteOrder.LITTLE_ENDIAN); // 设置为小端模式
        long processedSrcApkDataSize = bb.getLong(); // 读取这8个字节作为long类型的值
        byte[] processedSrcApkData=Arrays.copyOfRange(dexFileData, (int) (length - 8 - processedSrcApkDataSize), length - 8);
        byte[] srcApkData=reverseProcessApkData(processedSrcApkData);
        return srcApkData;
    }

    private byte[] reverseProcessApkData(byte[] processedApkData){
        /* 还原apk数据，解密解压等 */
        return processedApkData;
    }


    @Override
    protected void attachBaseContext(Context base) {
        Log.i("attachBaseContext", "attachBaseContext");
        super.attachBaseContext(base);
        try {
            byte[] dexFileData=this.readDexFileFromApk();
            byte[] srcApkData=this.splitSrcApkFromDex(dexFileData);
            // 创建储存apk的文件夹，写入src.apk
            File apkDir=base.getDir("apk_out",MODE_PRIVATE);
            srcApkPath=apkDir.getAbsolutePath()+"/src.apk";
            File srcApkFile = new File(srcApkPath);
            srcApkFile.setWritable(true);
            FileOutputStream fos=new FileOutputStream(srcApkFile);
            fos.write(srcApkData);
            fos.close();
            srcApkFile.setReadOnly(); // 受安卓安全策略影响，dex必须为只读
            Log.i("attachBaseContext","Write src.apk into "+srcApkPath);
            // 新建加载器
            File optDir =base.getDir("opt_dex",MODE_PRIVATE);
            File libDir =base.getDir("lib_dex",MODE_PRIVATE);
            optDirPath =optDir.getAbsolutePath();
            libDirPath =libDir.getAbsolutePath();
            ClassLoader pathClassLoader = ProxyApplication.class.getClassLoader();
            DexClassLoader dexClassLoader=new DexClassLoader(srcApkPath, optDirPath, libDirPath,pathClassLoader);
            Log.i("attachBaseContext","Successfully initiate DexClassLoader.");
            // 修正加载器
            replaceClassLoader1(base,dexClassLoader);
            Log.i("attachBaseContext","ClassLoader replaced.");

            //RefinvokeMethod.setField("","");
            //Log.i("attachBaseContext",RefinvokeMethod.getField("dalvik.system.PathClassLoader",cl,"pathList").toString());
//            try {
//                Object objectMain = dexClassLoader.loadClass("com.jok.myapplication.MainActivity");
//                Log.i("demo","MainActivity类加载完毕");
//            } catch (ClassNotFoundException e) {
//                e.printStackTrace();
//            }
        } catch (Exception e) {
            Log.i("demo", "error:" + Log.getStackTraceString(e));
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        loadResources(srcApkPath);
        String applicationName="";
        ApplicationInfo ai=null;
        try {
            ai=getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            if (ai.metaData!=null){
                applicationName=ai.metaData.getString("ApplicationName");
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        Log.i("onCreate",applicationName);
        // 将当前进程的mApplication设置为null
        Object activityThreadObj=RefinvokeMethod.invokeStaticMethod("android.app.ActivityThread","currentActivityThread",new Class[]{},new Object[]{});
        Object mBoundApplication=RefinvokeMethod.getField("android.app.ActivityThread",activityThreadObj,"mBoundApplication");
        Object info=RefinvokeMethod.getField("android.app.ActivityThread$AppBindData",mBoundApplication,"info");
        RefinvokeMethod.setField("android.app.LoadedApk","mApplication",info,null);
        Log.i("OnCreate", (String) RefinvokeMethod.getField("android.app.LoadedApk",info,"mPackageName"));

        // 从ActivityThread的mAllApplications中移除mInitialApplication
        Object mInitApplication=RefinvokeMethod.getField("android.app.ActivityThread",activityThreadObj,"mInitialApplication");
        ArrayList<Application> mAllApplications= (ArrayList<Application>) RefinvokeMethod.getField("android.app.ActivityThread",activityThreadObj,"mAllApplications");
        mAllApplications.remove(mInitApplication);

        // 更新两处className
        ApplicationInfo mApplicationInfo= (ApplicationInfo) RefinvokeMethod.getField("android.app.LoadedApk",info,"mApplicationInfo");
        ApplicationInfo appinfo= (ApplicationInfo) RefinvokeMethod.getField("android.app.ActivityThread$AppBindData",mBoundApplication,"appInfo");
        mApplicationInfo.className=applicationName;
        appinfo.className=applicationName;

        // 执行makeApplication(false,null)
        // todo:消除已启动报错
        Application app= (Application) RefinvokeMethod.invokeMethod("android.app.LoadedApk","makeApplication",info,new Class[]{boolean.class, Instrumentation.class},new Object[]{false,null});

        // 替换ActivityThread中mInitialApplication
        RefinvokeMethod.setField("android.app.ActivityThread","mInitialApplication",activityThreadObj,app);

        // 更新ContentProvider
        ArrayMap mProviderMap= (ArrayMap) RefinvokeMethod.getField("android.app.ActivityThread",activityThreadObj,"mProviderMap");
        Iterator iterator=mProviderMap.values().iterator();
        while (iterator.hasNext()){
            Object mProviderClientRecord=iterator.next();
            Object mLocalProvider=RefinvokeMethod.getField("android.app.ActivityThread$ProviderClientRecord",mProviderClientRecord,"mLocalProvider");
            RefinvokeMethod.setField("android.content.ContentProvider","mContext",mLocalProvider,app);
        }

        // 执行新app的onCreate方法
        app.onCreate();

    }
    protected AssetManager assetManager;
    protected Resources resources;
    protected Resources.Theme theme;

    protected void loadResources(String dexPath){
        try {
            AssetManager manager=AssetManager.class.newInstance();
            Method method=manager.getClass().getMethod("addAssetPath",String.class);
            method.invoke(manager,dexPath);
            assetManager=manager;
        } catch (Exception e) {
            e.printStackTrace();
        }
        Resources superRes = super.getResources();
        superRes.getDisplayMetrics();
        superRes.getConfiguration();
        resources = new Resources(assetManager, superRes.getDisplayMetrics(),superRes.getConfiguration());
        theme = resources.newTheme();
        theme.setTo(super.getTheme());
    }

    @Override
    public AssetManager getAssets() {
        return assetManager == null?super.getAssets():assetManager;
    }

    @Override
    public Resources getResources() {
        return resources==null?super.getResources():resources;
    }

    @Override
    public Resources.Theme getTheme() {
        return theme==null?super.getTheme():theme;
    }

}
