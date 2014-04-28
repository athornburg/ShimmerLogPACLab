package io.pacmonitorandroid.app.tools;

import android.app.FragmentManager;
import android.content.Context;
import android.util.Log;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.common.collect.Multimap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.pacmonitorandroid.app.driver.driver.FormatCluster;
import io.pacmonitorandroid.app.driver.driver.ObjectCluster;
import io.pacmonitorandroid.app.google.cloud.core.CloudBackendPatient;
import io.pacmonitorandroid.app.google.cloud.core.CloudCallbackHandler;
import io.pacmonitorandroid.app.google.cloud.core.CloudEntity;
import io.pacmonitorandroid.app.google.cloud.core.Consts;

public class Logging {
    boolean mFirstWrite=true;
    String[] mSensorNames;
    String[] mSensorFormats;
    String patientId = UUID.randomUUID().toString();
    List<CloudEntity>buffer;
    String[] mSensorUnits;
    String mDelimiter=","; //default is comma
    Context context;
    CloudBackendPatient cloudBackend;
    FragmentManager fragmentManager;
    private GoogleAccountCredential mCredential;

    /**
     * @param myName is the file name which will be used
     */
    public Logging(String myName,Context context){
       cloudBackend = new CloudBackendPatient(context);
        mCredential = GoogleAccountCredential.usingAudience(context, Consts.AUTH_AUDIENCE);
        cloudBackend.setCredential(mCredential);
        this.context = context;
        buffer = new ArrayList<CloudEntity>();
    }

    public Logging(String myName,String delimiter,Context context){
        cloudBackend = new CloudBackendPatient(context);
        mCredential = GoogleAccountCredential.usingAudience(context, Consts.AUTH_AUDIENCE);
        cloudBackend.setCredential(mCredential);
        this.context = context;
        buffer = new ArrayList<CloudEntity>();
    }



    /**
     * This function takes an object cluster and logs all the data within it. User should note that the function will write over prior files with the same name.
     * @param objectCluster data which will be written into the file
     */
    public void logData(ObjectCluster objectCluster){
        ObjectCluster objectClusterLog = objectCluster;

            if (mFirstWrite==true) {

                //First retrieve all the unique keys from the objectClusterLog
                Multimap<String, FormatCluster> m = objectClusterLog.mPropertyCluster;

                int size = m.size();
                mSensorNames = new String[size];
                mSensorFormats = new String[size];
                mSensorUnits = new String[size];
                int i = 0;
                int p = 0;
                for (String key : m.keys()) {
                    //first check that there are no repeat entries

                    if (compareStringArray(mSensorNames, key) == true) {
                        for (FormatCluster formatCluster : m.get(key)) {
                            mSensorFormats[p] = formatCluster.mFormat;
                            mSensorUnits[p] = formatCluster.mUnits;
                            p++;
                        }

                    }

                    mSensorNames[i] = key;
                    i++;
                }

            }


            SensorData data = new SensorData();
            for (int r=0;r<mSensorNames.length;r++) {
                Collection<FormatCluster> dataFormats = objectClusterLog.mPropertyCluster.get(mSensorNames[r]);
                FormatCluster formatCluster = (FormatCluster) returnFormatCluster(dataFormats,mSensorFormats[r],mSensorUnits[r]);  // retrieve the calibrated data
                Log.d("Shimmer","Data : " +mSensorNames[r] + formatCluster.mData + " "+ formatCluster.mUnits);
                switch(r){
                    case 6:data.setXa(formatCluster.mData);
                    case 11:data.setZa(formatCluster.mData);
                    case 12:data.setYa(formatCluster.mData);
                    case 16:data.setZm(formatCluster.mData);
                    case 19:data.setXm(formatCluster.mData);
                    case 20:data.setYm(formatCluster.mData);
                    case 27:data.setYg(formatCluster.mData);
                    case 29:data.setZg(formatCluster.mData);
                    case 34:data.setXg(formatCluster.mData);
                }
                data.setDate(Calendar.getInstance().getTime());
                data.setCurrentTime(System.currentTimeMillis());
                data.setWhereIsDevice("wrist");
                data.setActivity("walking");
                data.setId(patientId);

        }
        double seconds = data.getCurrentTime()/1000.00;
        CloudEntity ce = new CloudEntity("PatientData");
        ce.put("date", data.getDate());
        ce.put("seconds",seconds);
        ce.put("PatientId",data.getId());
        ce.put("XA",data.getXa());
        ce.put("ZA",data.getZa());
        ce.put("YA",data.getYa());
        ce.put("ZM",data.getZm());
        ce.put("XM",data.getXm());
        ce.put("YM",data.getYm());
        ce.put("YG",data.getYg());
        ce.put("ZG",data.getZg());
        ce.put("XG",data.getXg());
        ce.put("Activity",data.getActivity());
        ce.put("WhereIsDevice",data.getWhereIsDevice());
        buffer.add(ce);

        if(buffer.size()>20){
            CloudCallbackHandler<List<CloudEntity>> handler = new CloudCallbackHandler<List<CloudEntity>>() {
                @Override
                public void onComplete(final List<CloudEntity> result) {
                    Log.e("Result",result.toString());
                }

                @Override
                public void onError(final IOException exception) {
                    Log.e("Error",exception.toString());
                }
            };
            ExecutorService executor = Executors.newFixedThreadPool(5);
            executor.execute(cloudBackend.insertAll(buffer, handler));
            executor.shutdown();
            while (!executor.isTerminated()) {
            }
            buffer.clear();

        }
    }

    private boolean compareStringArray(String[] stringArray, String string){
        boolean uniqueString=true;
        int size = stringArray.length;
        for (int i=0;i<size;i++){
            if (stringArray[i]==string){
                uniqueString=false;
            }

        }
        return uniqueString;
    }

    private FormatCluster returnFormatCluster(Collection<FormatCluster> collectionFormatCluster, String format, String units){
        Iterator<FormatCluster> iFormatCluster=collectionFormatCluster.iterator();
        FormatCluster formatCluster;
        FormatCluster returnFormatCluster = null;

        while(iFormatCluster.hasNext()){
            formatCluster=(FormatCluster)iFormatCluster.next();
            if (formatCluster.mFormat==format && formatCluster.mUnits==units){
                returnFormatCluster=formatCluster;
            }
        }
        return returnFormatCluster;
    }

}


