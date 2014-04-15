package io.pacmonitorandroid.app.tools;

import java.util.Date;

/**
 * Created by alexthornburg on 3/30/14.
 */
public class SensorData {

    private String id;
    private double xa;
    private double ya;
    private double za;
    private double xm;
    private double ym;
    private double zm;
    private double xg;
    private double yg;
    private double zg;
    private String activity;
    private String whereIsDevice;
    private Date date;
    private long currentTime;


    public long getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(long currentTime) {
        this.currentTime = currentTime;
    }

    public String getId(){
        return id;
    }

    public void setId(String id){
        this.id = id;
    }

    public double getXa(){
        return xa;
    }

    public void setXa(double xa){
        this.xa=xa;
    }

    public double getYa(){
        return ya;
    }

    public void setYa(double ya){
        this.ya = ya;
    }

    public double getZa(){
        return za;
    }

    public void setZa(double za){
        this.za = za;
    }

    public double getXm(){
        return xm;
    }

    public void setXm(double xm){
        this.xm = xm;
    }

    public double getYm(){
        return ym;
    }

    public void setYm(double ym){
        this.ym = ym;
    }

    public double getZm(){
        return zm;
    }

    public void setZm(double zm){
        this.zm = zm;
    }

    public double getXg(){
        return xg;
    }

    public void setXg(double xg){
        this.xg = xg;
    }

    public double getYg(){
        return yg;
    }

    public void setYg(double yg){
        this.yg = yg;
    }

    public double getZg(){
        return zg;
    }

    public void setZg(double zg){
        this.zg = zg;
    }

    public String getActivity(){
        return activity;
    }

    public void setActivity(String activity){
        this.activity = activity;
    }

    public String getWhereIsDevice(){
        return whereIsDevice;
    }

    public void setWhereIsDevice(String whereIsDevice){
        this.whereIsDevice = whereIsDevice;
    }

    public void setDate(Date date){this.date = date;}

    public Date getDate(){return date;}




}
