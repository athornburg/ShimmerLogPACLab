//Rev_1.4.01
/*
 * Copyright (c) 2010, Shimmer Research, Ltd.
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:

 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Shimmer Research, Ltd. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @author Jong Chern Lim
 * @date   October, 2013
 * 
 *  Changes since 1.4
 *  - removed mSamplingRate decimal formatter, decimal formatter should be done on the UI
 * 
 */



package io.pacmonitorandroid.app.driver.bluetooth;

import io.pacmonitorandroid.app.driver.driver.Configuration;
import io.pacmonitorandroid.app.driver.driver.ObjectCluster;
import io.pacmonitorandroid.app.driver.driver.ShimmerObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.Timer;


public abstract class ShimmerBluetooth extends ShimmerObject {
	
	protected int mSetEnabledSensors = SENSOR_ACCEL;								// Only used during the initialization process, see initialize();
	// Constants that indicate the current connection state
	
	public static final int STATE_NONE = 0;       // The class is doing nothing
	public static final int STATE_CONNECTING = 1; // The class is now initiating an outgoing connection
	public static final int STATE_CONNECTED = 2;  // The class is now connected to a remote device
	protected boolean mInstructionStackLock = false;
	protected int mState;
	protected byte mCurrentCommand;	
	protected boolean mWaitForAck=false;                                          // This indicates whether the device is waiting for an acknowledge packet from the Shimmer Device  
	protected boolean mWaitForResponse=false; 									// This indicates whether the device is waiting for a response packet from the Shimmer Device 
	protected boolean mTransactionCompleted=true;									// Variable is used to ensure a command has finished execution prior to executing the next command (see initialize())
	protected IOThread mIOThread;
	protected boolean mContinousSync=false;                                       // This is to select whether to continuously check the data packets 
	protected boolean mSetupDevice=false;		
	protected Stack<Byte> byteStack = new Stack<Byte>();
	protected abstract void connect(String address,String bluetoothLibrary);
	protected abstract void dataHandler(ObjectCluster ojc);
	protected abstract boolean bytesToBeRead();
	protected abstract int availableBytes();
	protected abstract void startResponseTimer(int duration);
	protected double mLowBattLimit=3.4;
	protected abstract void writeBytes(byte[] data);
	protected abstract void stop();
	protected abstract void isNowStreaming();
	protected abstract void hasStopStreaming();
	protected abstract void sendStatusMsgPacketLossDetected();
	protected abstract void inquiryDone();
	protected abstract void sendStatusMSGtoUI(String msg);
	protected abstract void printLogDataForDebugging(String msg);
	protected abstract void isReadyForStreaming();
	protected abstract void connectionLost();
	protected abstract void setState(int state);
	protected boolean mInitialized = false;
	protected abstract byte[] readBytes(int numberofBytes);
	protected abstract byte readByte();
	protected List<byte []> mListofInstructions = new  ArrayList<byte[]>();
	private final int ACK_TIMER_DURATION = 2; 									// Duration to wait for an ack packet (seconds)
	protected Timer mTimer;														// Timer variable used when waiting for an ack or response packet
	protected boolean mDummy=false;
	protected boolean mFirstTime=true;
	private byte mTempByteValue;												// A temporary variable used to store Byte value	
	protected int mTempIntValue;													// A temporary variable used to store Integer value, used mainly to store a value while waiting for an acknowledge packet (e.g. when writeGRange() is called, the range is stored temporarily and used to update GSRRange when the acknowledge packet is received.
	protected int tempEnabledSensors;												// This stores the enabled sensors
	protected boolean mSync=true;													// Variable to keep track of sync
	
	public class IOThread extends Thread {
		byte[] tb ={0};
		byte[] newPacket=new byte[mPacketSize+1];
		public boolean stop = false;
		public synchronized void run() {
			while (!stop) {
				/////////////////////////
				// is an instruction running ? if not proceed
				if (mInstructionStackLock==false){
					// check instruction stack, are there any other instructions left to be executed?
					if (!mListofInstructions.isEmpty()){
						mInstructionStackLock=true;
						byte[] insBytes = (byte[]) mListofInstructions.get(0);
						mCurrentCommand=insBytes[0];
						mWaitForAck=true;
						String msg = "Command Transmitted: " + Arrays.toString(insBytes);
						printLogDataForDebugging(msg);
						writeBytes(insBytes);
						if (mCurrentCommand==STOP_STREAMING_COMMAND){
							mStreaming=false;
						} else {
							if (mCurrentCommand==GET_FW_VERSION_COMMAND){
								startResponseTimer(ACK_TIMER_DURATION);
							} else if (mCurrentCommand==GET_SAMPLING_RATE_COMMAND){
								startResponseTimer(ACK_TIMER_DURATION);
							} else {
								startResponseTimer(ACK_TIMER_DURATION+10);
							}
						}
						mTransactionCompleted=false;
					}

				}
				
				
				if (mWaitForAck==true && mStreaming ==false) {

					if (bytesToBeRead()){
						tb=readBytes(1);
						String msg="";
						//	msg = "rxb resp : " + Arrays.toString(tb);
						//	printLogDataForDebugging(msg);

						if (mCurrentCommand==STOP_STREAMING_COMMAND) { //due to not receiving the ack from stop streaming command we will skip looking for it.
							mTimer.cancel();
							mTimer.purge();
							mStreaming=false;
							mTransactionCompleted=true;
							mWaitForAck=false;
							try {
								Thread.sleep(200);	// Wait to ensure that we dont missed any bytes which need to be cleared
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							byteStack.clear();

							while(availableBytes()>0){ //this is to clear the buffer 

								tb=readBytes(availableBytes());

							}
							hasStopStreaming();
							mListofInstructions.remove(0);
							mInstructionStackLock=false;
						}

						if ((byte)tb[0]==ACK_COMMAND_PROCESSED)
						{	
							msg = "Ack Received for Command: " + Byte.toString(mCurrentCommand);
							printLogDataForDebugging(msg);
							if (mCurrentCommand==START_STREAMING_COMMAND) {
								mTimer.cancel();
								mTimer.purge();
								mStreaming=true;
								mTransactionCompleted=true;
								byteStack.clear();
								isNowStreaming();
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}

							else if (mCurrentCommand==SET_SAMPLING_RATE_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted=true;
								mWaitForAck=false;
								byte[] instruction=mListofInstructions.get(0);
								double tempdouble=-1;
								if (mShimmerVersion==SHIMMER_2 || mShimmerVersion==SHIMMER_2R){
									tempdouble=(double)1024/instruction[1];
								} else {
									tempdouble = 32768/(double)((int)(instruction[1] & 0xFF) + ((int)(instruction[2] & 0xFF) << 8));
								}
								mSamplingRate = tempdouble;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_BUFFER_SIZE_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted = true;
								mWaitForAck=false;
								mBufferSize=(int)((byte[])mListofInstructions.get(0))[1];
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==INQUIRY_COMMAND) {
								mWaitForResponse=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_LSM303DLHC_ACCEL_LPMODE_COMMAND) {
								mWaitForResponse=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_LSM303DLHC_ACCEL_HRMODE_COMMAND) {
								mWaitForResponse=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_BUFFER_SIZE_COMMAND) {
								mWaitForAck=false;
								mWaitForResponse=true;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_BLINK_LED) {
								mWaitForAck=false;
								mWaitForResponse=true;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_MAG_SAMPLING_RATE_COMMAND) {
								mWaitForAck=false;
								mWaitForResponse=true;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_MAG_GAIN_COMMAND) {
								mWaitForAck=false;
								mWaitForResponse=true;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_ACCEL_SENSITIVITY_COMMAND) {
								mWaitForAck=false;
								mWaitForResponse=true;
							}
							else if (mCurrentCommand==GET_MPU9150_GYRO_RANGE_COMMAND) {
								mWaitForAck=false;
								mWaitForResponse=true;
							}
							else if (mCurrentCommand==GET_GSR_RANGE_COMMAND) {
								mWaitForAck=false;
								mWaitForResponse=true;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_FW_VERSION_COMMAND) {
								mWaitForResponse = true;
								mWaitForAck=false;
							}
							else if (mCurrentCommand==GET_ECG_CALIBRATION_COMMAND) {
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_EMG_CALIBRATION_COMMAND) {
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==SET_BLINK_LED) {
								mCurrentLEDStatus=(int)((byte[])mListofInstructions.get(0))[1];
								mTransactionCompleted = true;
								//mWaitForAck=false;
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_GSR_RANGE_COMMAND) {

								mTransactionCompleted = true;
								mWaitForAck=false;
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mGSRRange=(int)((byte [])mListofInstructions.get(0))[1];
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==GET_SAMPLING_RATE_COMMAND) {
								mWaitForResponse=true;
								mWaitForAck=false;

							}
							else if (mCurrentCommand==GET_CONFIG_BYTE0_COMMAND) {
								mWaitForResponse=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==SET_CONFIG_BYTE0_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mConfigByte0=(int)((byte [])mListofInstructions.get(0))[1];
								mWaitForAck=false;
								mTransactionCompleted=true;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} else if (mCurrentCommand==SET_LSM303DLHC_ACCEL_LPMODE_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mWaitForAck=false;
								mTransactionCompleted=true;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} else if (mCurrentCommand==SET_LSM303DLHC_ACCEL_HRMODE_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mWaitForAck=false;
								mTransactionCompleted=true;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_PMUX_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								if (((byte[])mListofInstructions.get(0))[1]==1) {
									mConfigByte0=(byte) ((byte) (mConfigByte0|64)&(0xFF)); 
								}
								else if (((byte[])mListofInstructions.get(0))[1]==0) {
									mConfigByte0=(byte) ((byte)(mConfigByte0 & 191)&(0xFF));
								}
								mTransactionCompleted=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if(mCurrentCommand==SET_BMP180_PRES_RESOLUTION_COMMAND){
								mTimer.cancel(); //cancel the ack timer
								mPressureResolution=(int)((byte [])mListofInstructions.get(0))[1];
								mTransactionCompleted=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_GYRO_TEMP_VREF_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted=true;
								mConfigByte0=mTempByteValue;
								mWaitForAck=false;
							}
							else if (mCurrentCommand==SET_5V_REGULATOR_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								if (((byte[])mListofInstructions.get(0))[1]==1) {
									mConfigByte0=(byte) (mConfigByte0|128); 
								}
								else if (((byte[])mListofInstructions.get(0))[1]==0) {
									mConfigByte0=(byte)(mConfigByte0 & 127);
								}
								mTransactionCompleted=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_INTERNAL_EXP_POWER_ENABLE_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								if (((byte[])mListofInstructions.get(0))[1]==1) {
									mConfigByte0 = (mConfigByte0|16777216); 
									mInternalExpPower = 1;
								}
								else if (((byte[])mListofInstructions.get(0))[1]==0) {
									mConfigByte0 = mConfigByte0 & 4278190079l;
									mInternalExpPower = 0;
								}
								mTransactionCompleted=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_ACCEL_SENSITIVITY_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mAccelRange=(int)(((byte[])mListofInstructions.get(0))[1]);
								if (mDefaultCalibrationParametersAccel == true){
									if (mShimmerVersion != SHIMMER_3){
										if (getAccelRange()==0){
											SensitivityMatrixAccel = SensitivityMatrixAccel1p5gShimmer2; 
										} else if (getAccelRange()==1){
											SensitivityMatrixAccel = SensitivityMatrixAccel2gShimmer2; 
										} else if (getAccelRange()==2){
											SensitivityMatrixAccel = SensitivityMatrixAccel4gShimmer2; 
										} else if (getAccelRange()==3){
											SensitivityMatrixAccel = SensitivityMatrixAccel6gShimmer2; 
										}
									} else if(mShimmerVersion == SHIMMER_3){
										SensitivityMatrixAccel = SensitivityMatrixLowNoiseAccel2gShimmer3;
										AlignmentMatrixAccel = AlignmentMatrixLowNoiseAccelShimmer3;
										OffsetVectorAccel = OffsetVectorLowNoiseAccelShimmer3;
									}
								}

								if (mDefaultCalibrationParametersDigitalAccel){
									if (mShimmerVersion == SHIMMER_3){
										if (getAccelRange()==1){
											SensitivityMatrixAccel2 = SensitivityMatrixWideRangeAccel4gShimmer3;
											AlignmentMatrixAccel2 = AlignmentMatrixWideRangeAccelShimmer3;
											OffsetVectorAccel2 = OffsetVectorWideRangeAccelShimmer3;
										} else if (getAccelRange()==2){
											SensitivityMatrixAccel2 = SensitivityMatrixWideRangeAccel8gShimmer3;
											AlignmentMatrixAccel2 = AlignmentMatrixWideRangeAccelShimmer3;
											OffsetVectorAccel2 = OffsetVectorWideRangeAccelShimmer3;
										} else if (getAccelRange()==3){
											SensitivityMatrixAccel2 = SensitivityMatrixWideRangeAccel16gShimmer3;
											AlignmentMatrixAccel2 = AlignmentMatrixWideRangeAccelShimmer3;
											OffsetVectorAccel2 = OffsetVectorWideRangeAccelShimmer3;
										} else if (getAccelRange()==0){
											SensitivityMatrixAccel2 = SensitivityMatrixWideRangeAccel2gShimmer3;
											AlignmentMatrixAccel2 = AlignmentMatrixWideRangeAccelShimmer3;
											OffsetVectorAccel2 = OffsetVectorWideRangeAccelShimmer3;
										}
									}
								}
								mTransactionCompleted=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} 
							else if (mCurrentCommand==SET_MPU9150_GYRO_RANGE_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mGyroRange=(int)(((byte[])mListofInstructions.get(0))[1]);
								if (mDefaultCalibrationParametersGyro == true){
									if(mShimmerVersion == SHIMMER_3){
										AlignmentMatrixGyro = AlignmentMatrixGyroShimmer3;
										OffsetVectorGyro = OffsetVectorGyroShimmer3;
										if (mGyroRange==0){
											SensitivityMatrixGyro = SensitivityMatrixGyro250dpsShimmer3;

										} else if (mGyroRange==1){
											SensitivityMatrixGyro = SensitivityMatrixGyro500dpsShimmer3;

										} else if (mGyroRange==2){
											SensitivityMatrixGyro = SensitivityMatrixGyro1000dpsShimmer3;

										} else if (mGyroRange==3){
											SensitivityMatrixGyro = SensitivityMatrixGyro2000dpsShimmer3;

										}
									}
								}
								mTransactionCompleted=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} 
							else if (mCurrentCommand==SET_MAG_SAMPLING_RATE_COMMAND){
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted = true;
								mMagSamplingRate = mTempIntValue;
								mWaitForAck = false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} else if (mCurrentCommand==GET_ACCEL_SAMPLING_RATE_COMMAND){
								mWaitForAck=false;
								mWaitForResponse=true;
								mListofInstructions.remove(0);
							} else if (mCurrentCommand==SET_ACCEL_SAMPLING_RATE_COMMAND){
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted = true;
								mAccelSamplingRate = mTempIntValue;
								mWaitForAck = false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} else if (mCurrentCommand==SET_MPU9150_SAMPLING_RATE_COMMAND){
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted = true;
								mMPU9150SamplingRate = mTempIntValue;
								mWaitForAck = false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} else if (mCurrentCommand==SET_SENSORS_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mWaitForAck=false;
								mEnabledSensors=tempEnabledSensors;
								byteStack.clear(); // Always clear the packetStack after setting the sensors, this is to ensure a fresh start
								mTransactionCompleted=true;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_MAG_GAIN_COMMAND){
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted = true;
								mWaitForAck = false;
								mMagGain=(int)((byte [])mListofInstructions.get(0))[1];
								if (mDefaultCalibrationParametersMag == true){
									if(mShimmerVersion == SHIMMER_3){
										AlignmentMatrixMag = AlignmentMatrixMagShimmer3;
										OffsetVectorMag = OffsetVectorMagShimmer3;
										if (mMagGain==1){
											SensitivityMatrixMag = SensitivityMatrixMag1p3GaShimmer3;
										} else if (mMagGain==2){
											SensitivityMatrixMag = SensitivityMatrixMag1p9GaShimmer3;
										} else if (mMagGain==3){
											SensitivityMatrixMag = SensitivityMatrixMag2p5GaShimmer3;
										} else if (mMagGain==4){
											SensitivityMatrixMag = SensitivityMatrixMag4GaShimmer3;
										} else if (mMagGain==5){
											SensitivityMatrixMag = SensitivityMatrixMag4p7GaShimmer3;
										} else if (mMagGain==6){
											SensitivityMatrixMag = SensitivityMatrixMag5p6GaShimmer3;
										} else if (mMagGain==7){
											SensitivityMatrixMag = SensitivityMatrixMag8p1GaShimmer3;
										}
									}
								}
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==GET_ACCEL_CALIBRATION_COMMAND || mCurrentCommand==GET_GYRO_CALIBRATION_COMMAND || mCurrentCommand==GET_MAG_CALIBRATION_COMMAND || mCurrentCommand==GET_ALL_CALIBRATION_COMMAND) {
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_SHIMMER_VERSION_COMMAND_NEW) {
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_BMP180_CALIBRATION_COEFFICIENTS_COMMAND) {
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							} else if (mCurrentCommand==GET_SHIMMER_VERSION_COMMAND) {
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==SET_ECG_CALIBRATION_COMMAND){
								//mGSRRange=mTempIntValue;
								mDefaultCalibrationParametersECG = false;
								OffsetECGLALL=(double)((((byte[])mListofInstructions.get(0))[0]&0xFF)<<8)+(((byte[])mListofInstructions.get(0))[1]&0xFF);
								GainECGLALL=(double)((((byte[])mListofInstructions.get(0))[2]&0xFF)<<8)+(((byte[])mListofInstructions.get(0))[3]&0xFF);
								OffsetECGRALL=(double)((((byte[])mListofInstructions.get(0))[4]&0xFF)<<8)+(((byte[])mListofInstructions.get(0))[5]&0xFF);
								GainECGRALL=(double)((((byte[])mListofInstructions.get(0))[6]&0xFF)<<8)+(((byte[])mListofInstructions.get(0))[7]&0xFF);
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_EMG_CALIBRATION_COMMAND){
								//mGSRRange=mTempIntValue;
								mDefaultCalibrationParametersEMG = false;
								OffsetEMG=(double)((((byte[])mListofInstructions.get(0))[0]&0xFF)<<8)+(((byte[])mListofInstructions.get(0))[1]&0xFF);
								GainEMG=(double)((((byte[])mListofInstructions.get(0))[2]&0xFF)<<8)+(((byte[])mListofInstructions.get(0))[3]&0xFF);
								mTransactionCompleted = true;
								mWaitForAck=false;
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==TOGGLE_LED_COMMAND){
								//mGSRRange=mTempIntValue;
								mTransactionCompleted = true;
								mWaitForAck=false;
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}

						}


					}
				} else if (mWaitForResponse==true) {
					if (mFirstTime){
						while (availableBytes()!=0){
								if (bytesToBeRead()){
									tb=readBytes(1);
									String msg = "First Time : " + Arrays.toString(tb);
									printLogDataForDebugging(msg);
									
								}
							
						}
						mFirstTime=false;
					} else if (availableBytes()!=0){

						tb=readBytes(1);
						
						String msg="";
						//msg = "rxb : " + Arrays.toString(tb);
						//printLogDataForDebugging(msg);
						
						if (tb[0]==FW_VERSION_RESPONSE){
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();

							try {
								Thread.sleep(200);	// Wait to ensure the packet has been fully received
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							byte[] bufferInquiry = new byte[6]; 
							bufferInquiry = readBytes(6);
							mFWIdentifier=(double)((bufferInquiry[1]&0xFF)<<8)+(double)(bufferInquiry[0]&0xFF);
							mFWVersion=(double)((bufferInquiry[3]&0xFF)<<8)+(double)(bufferInquiry[2]&0xFF)+((double)((bufferInquiry[4]&0xFF))/10);
							mFWInternal=(int)(bufferInquiry[5]&0xFF);
							if (((double)((bufferInquiry[4]&0xFF))/10)==0){
								mFWVersionFullName = "BtStream " + Double.toString(mFWVersion) + "."+ Integer.toString(mFWInternal);
							} else {
								mFWVersionFullName = "BtStream " + Double.toString(mFWVersion) + "."+ Integer.toString(mFWInternal);
							}
							msg = "FW Version Response Received: " +mFWVersionFullName;
							printLogDataForDebugging(msg);
							mListofInstructions.remove(0);
							mInstructionStackLock=false;
							mTransactionCompleted=true;
							readShimmerVersion();
						} else if (tb[0]==BMP180_CALIBRATION_COEFFICIENTS_RESPONSE){
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							
							//get pressure
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							
							byte[] pressureResoRes = new byte[22]; 
						
							pressureResoRes = readBytes(22);
							mPressureCalRawParams = new byte[23];
							System.arraycopy(pressureResoRes, 0, mPressureCalRawParams, 1, 22);
							mPressureCalRawParams[0] = tb[0];
							retrievepressurecalibrationparametersfrompacket(pressureResoRes,tb[0]);
							msg = "BMP180 Response Received";
							printLogDataForDebugging(msg);
							mInstructionStackLock=false;
						} else if (tb[0]==INQUIRY_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							try {
								Thread.sleep(500);	// Wait to ensure the packet has been fully received
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							List<Byte> buffer = new  ArrayList<Byte>();
							if (!(mShimmerVersion==SHIMMER_3))
							{
								 for (int i = 0; i < 5; i++)
	                                {
	                                    // get Sampling rate, accel range, config setup byte0, num chans and buffer size
	                                    buffer.add(readByte());
	                                }
								 
	                                for (int i = 0; i < (int)buffer.get(3); i++)
	                                {
	                                    // read each channel type for the num channels
	                                	buffer.add(readByte());
	                                }
							}
							else
							{
								  for (int i = 0; i < 8; i++)
	                                {
	                                    // get Sampling rate, accel range, config setup byte0, num chans and buffer size
									  buffer.add(readByte());
	                                }
	                                for (int i = 0; i < (int)buffer.get(6); i++)
	                                {
	                                    // read each channel type for the num channels
	                                	buffer.add(readByte());
	                                }
							}
							byte[] bufferInquiry = new byte[buffer.size()];
							for (int i = 0; i < bufferInquiry.length; i++) {
								bufferInquiry[i] = (byte) buffer.get(i);
							}
								
							msg = "Inquiry Response Received: " + Arrays.toString(bufferInquiry);
							printLogDataForDebugging(msg);
							interpretInqResponse(bufferInquiry);
							inquiryDone();
							mWaitForResponse = false;
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						} else if(tb[0] == GSR_RANGE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferGSRRange = readBytes(1); 
							mGSRRange=bufferGSRRange[0];
							msg = "GSR Range Response Received: " + Arrays.toString(bufferGSRRange);
							printLogDataForDebugging(msg);
							mInstructionStackLock=false;
						} else if(tb[0] == MAG_SAMPLING_RATE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferAns = readBytes(1); 
							mMagSamplingRate=bufferAns[0];
							msg = "Mag Sampling Rate Response Received: " + Arrays.toString(bufferAns);
							printLogDataForDebugging(msg);
							mInstructionStackLock=false;
						} else if(tb[0] == ACCEL_SAMPLING_RATE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferAns = readBytes(1); 
							mAccelSamplingRate=bufferAns[0];
							mInstructionStackLock=false;
						} else if(tb[0] == MAG_GAIN_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferAns = readBytes(1); 
							mMagGain=bufferAns[0];
							mInstructionStackLock=false;
						} else if(tb[0] == LSM303DLHC_ACCEL_HRMODE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferAns = readBytes(1);
							mInstructionStackLock=false;
						} else if(tb[0] == LSM303DLHC_ACCEL_LPMODE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferAns = readBytes(1);
							mInstructionStackLock=false;
						} else if(tb[0]==BUFFER_SIZE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] byteled = readBytes(1);
							mBufferSize = byteled[0] & 0xFF;
							mInstructionStackLock=false;
						} else if(tb[0]==BLINK_LED_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] byteled = readBytes(1);
							mCurrentLEDStatus = byteled[0]&0xFF;
							mInstructionStackLock=false;
						} else if(tb[0]==ACCEL_SENSITIVITY_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferAccelSensitivity = readBytes(1);
							mAccelRange=bufferAccelSensitivity[0];
							if (mDefaultCalibrationParametersAccel == true){
								if (mShimmerVersion != SHIMMER_3){
									if (getAccelRange()==0){
										SensitivityMatrixAccel = SensitivityMatrixAccel1p5gShimmer2; 
									} else if (getAccelRange()==1){
										SensitivityMatrixAccel = SensitivityMatrixAccel2gShimmer2; 
									} else if (getAccelRange()==2){
										SensitivityMatrixAccel = SensitivityMatrixAccel4gShimmer2; 
									} else if (getAccelRange()==3){
										SensitivityMatrixAccel = SensitivityMatrixAccel6gShimmer2; 
									}
								} else if(mShimmerVersion == SHIMMER_3){
									if (getAccelRange()==0){
										SensitivityMatrixAccel = SensitivityMatrixLowNoiseAccel2gShimmer3;
										AlignmentMatrixAccel = AlignmentMatrixLowNoiseAccelShimmer3;
										OffsetVectorAccel = OffsetVectorLowNoiseAccelShimmer3;
									} else if (getAccelRange()==1){
										SensitivityMatrixAccel = SensitivityMatrixWideRangeAccel4gShimmer3;
										AlignmentMatrixAccel = AlignmentMatrixWideRangeAccelShimmer3;
										OffsetVectorAccel = OffsetVectorWideRangeAccelShimmer3;
									} else if (getAccelRange()==2){
										SensitivityMatrixAccel = SensitivityMatrixWideRangeAccel8gShimmer3;
										AlignmentMatrixAccel = AlignmentMatrixWideRangeAccelShimmer3;
										OffsetVectorAccel = OffsetVectorWideRangeAccelShimmer3;
									} else if (getAccelRange()==3){
										SensitivityMatrixAccel = SensitivityMatrixWideRangeAccel16gShimmer3;
										AlignmentMatrixAccel = AlignmentMatrixWideRangeAccelShimmer3;
										OffsetVectorAccel = OffsetVectorWideRangeAccelShimmer3;
									}
								}
							}   
							mListofInstructions.remove(0);
							mInstructionStackLock=false;
						} else if(tb[0]==MPU9150_GYRO_RANGE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferGyroSensitivity = readBytes(1);
							mGyroRange=bufferGyroSensitivity[0];
							if (mDefaultCalibrationParametersGyro == true){
								if(mShimmerVersion == SHIMMER_3){
									AlignmentMatrixGyro = AlignmentMatrixGyroShimmer3;
									OffsetVectorGyro = OffsetVectorGyroShimmer3;
									if (mGyroRange==0){
										SensitivityMatrixGyro = SensitivityMatrixGyro250dpsShimmer3;

									} else if (mGyroRange==1){
										SensitivityMatrixGyro = SensitivityMatrixGyro500dpsShimmer3;

									} else if (mGyroRange==2){
										SensitivityMatrixGyro = SensitivityMatrixGyro1000dpsShimmer3;

									} else if (mGyroRange==3){
										SensitivityMatrixGyro = SensitivityMatrixGyro2000dpsShimmer3;
									}
								}
							}   
							mListofInstructions.remove(0);
							mInstructionStackLock=false;
						}else if (tb[0]==SAMPLING_RATE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							if(mStreaming==false) {
								if (mShimmerVersion==SHIMMER_2R || mShimmerVersion==SHIMMER_2){    
									byte[] bufferSR = readBytes(1);
									if (mCurrentCommand==GET_SAMPLING_RATE_COMMAND) { // this is a double check, not necessary 
										double val=(double)(bufferSR[0] & (byte) ACK_COMMAND_PROCESSED);
										mSamplingRate=1024/val;
									}
								} else if (mShimmerVersion==SHIMMER_3){
									byte[] bufferSR = readBytes(2); //read the sampling rate
									mSamplingRate = 32768/(double)((int)(bufferSR[0] & 0xFF) + ((int)(bufferSR[1] & 0xFF) << 8));
								}
							}

							msg = "Sampling Rate Response Received: " + Double.toString(mSamplingRate);
							printLogDataForDebugging(msg);
							mTransactionCompleted=true;
							mListofInstructions.remove(0);
							mInstructionStackLock=false;
						} else if (tb[0]==ACCEL_CALIBRATION_RESPONSE ) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
								try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							mWaitForResponse=false;
							byte[] bufferCalibrationParameters = readBytes(21);
							
							mAccelCalRawParams = new byte[22];
							System.arraycopy(bufferCalibrationParameters, 0, mAccelCalRawParams, 1, 21);
							mAccelCalRawParams[0] = tb[0];
							
							int packetType=tb[0];
							retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, packetType);
							msg = "Accel Calibration Response Received";
							printLogDataForDebugging(msg);
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						}  else if (tb[0]==ALL_CALIBRATION_RESPONSE ) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
					
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							if (mShimmerVersion != SHIMMER_3){
								byte[] bufferCalibrationParameters = readBytes(21);
								mAccelCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mAccelCalRawParams, 1, 21);
								mAccelCalRawParams[0] = ACCEL_CALIBRATION_RESPONSE;
								
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, ACCEL_CALIBRATION_RESPONSE);

								//get gyro
								try {
									Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								bufferCalibrationParameters = readBytes(21);
								mGyroCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mGyroCalRawParams, 1, 21);
								mGyroCalRawParams[0] = GYRO_CALIBRATION_RESPONSE;
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, GYRO_CALIBRATION_RESPONSE);

								//get mag
								try {
									Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								bufferCalibrationParameters = readBytes(21);
								mMagCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mMagCalRawParams, 1, 21);
								mMagCalRawParams[0] = MAG_CALIBRATION_RESPONSE;
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, MAG_CALIBRATION_RESPONSE);

								try {
									Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								
								bufferCalibrationParameters = readBytes(4); 
								mEMGCalRawParams = new byte[5];
								System.arraycopy(bufferCalibrationParameters, 0, mEMGCalRawParams, 1, 4);
								mEMGCalRawParams[0] = EMG_CALIBRATION_RESPONSE;
								retrievebiophysicalcalibrationparametersfrompacket( bufferCalibrationParameters,EMG_CALIBRATION_RESPONSE);
								
							
								
								bufferCalibrationParameters = readBytes(8);
								
								mECGCalRawParams = new byte[9];
								System.arraycopy(bufferCalibrationParameters, 0, mECGCalRawParams, 1, 8);
								mECGCalRawParams[0] = ECG_CALIBRATION_RESPONSE;
								retrievebiophysicalcalibrationparametersfrompacket( bufferCalibrationParameters,ECG_CALIBRATION_RESPONSE);
								
								mTransactionCompleted=true;
								mInstructionStackLock=false;

							} else {


								byte[] bufferCalibrationParameters =readBytes(21); 
								
								mAccelCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mAccelCalRawParams, 1, 21);
								mAccelCalRawParams[0] = ACCEL_CALIBRATION_RESPONSE;
								
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, ACCEL_CALIBRATION_RESPONSE);

								//get gyro
								try {
									Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								bufferCalibrationParameters = readBytes(21); 
								
								mGyroCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mGyroCalRawParams, 1, 21);
								mGyroCalRawParams[0] = GYRO_CALIBRATION_RESPONSE;
								
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, GYRO_CALIBRATION_RESPONSE);

								//get mag
								try {
									Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								bufferCalibrationParameters = readBytes(21); 
								
								mMagCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mMagCalRawParams, 1, 21);
								mMagCalRawParams[0] = MAG_CALIBRATION_RESPONSE;
								
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, MAG_CALIBRATION_RESPONSE);

								//second accel cal params
								try {
									Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								bufferCalibrationParameters = readBytes(21);
								
								mDigiAccelCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mDigiAccelCalRawParams, 1, 21);
								mDigiAccelCalRawParams[0] = LSM303DLHC_ACCEL_CALIBRATION_RESPONSE;
								msg = "All Calibration Response Received";
								printLogDataForDebugging(msg);
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, LSM303DLHC_ACCEL_CALIBRATION_RESPONSE);
								mTransactionCompleted=true;
								mInstructionStackLock=false;

							}
						} else if (tb[0]==GYRO_CALIBRATION_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							mWaitForResponse=false;
							byte[] bufferCalibrationParameters = readBytes(21);
							mGyroCalRawParams = new byte[22];
							System.arraycopy(bufferCalibrationParameters, 0, mGyroCalRawParams, 1, 21);
							mGyroCalRawParams[0] = tb[0];
							
							int packetType=tb[0];
							retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, packetType);
							msg = "Gyro Calibration Response Received";
							printLogDataForDebugging(msg);
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						} else if (tb[0]==MAG_CALIBRATION_RESPONSE ) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							byte[] bufferCalibrationParameters = readBytes(21);
							mMagCalRawParams = new byte[22];
							System.arraycopy(bufferCalibrationParameters, 0, mMagCalRawParams, 1, 21);
							mMagCalRawParams[0] = tb[0];
							int packetType=tb[0];
							retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, packetType);
							msg = "Mag Calibration Response Received";
							printLogDataForDebugging(msg);
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						} else if(tb[0]==CONFIG_BYTE0_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							
							if (mShimmerVersion==SHIMMER_2R || mShimmerVersion==SHIMMER_2){    
								byte[] bufferConfigByte0 = readBytes(1);
								mConfigByte0 = bufferConfigByte0[0] & 0xFF;
							} else {
								byte[] bufferConfigByte0 = readBytes(4);
								mConfigByte0 = ((long)(bufferConfigByte0[0] & 0xFF) +((long)(bufferConfigByte0[1] & 0xFF) << 8)+((long)(bufferConfigByte0[2] & 0xFF) << 16) +((long)(bufferConfigByte0[3] & 0xFF) << 24));
							}
							msg = "ConfigByte0 response received Response Received";
							printLogDataForDebugging(msg);
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						} else if(tb[0]==GET_SHIMMER_VERSION_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							byte[] bufferShimmerVersion = new byte[1]; 
							bufferShimmerVersion = readBytes(1);
							mShimmerVersion=(int)bufferShimmerVersion[0];
							generateBiMapSensorIDtoSensorName();
							mTransactionCompleted=true;
							mInstructionStackLock=false;
							if (mShimmerVersion == SHIMMER_2R){
								initializeShimmer2R();
							} else if (mShimmerVersion == SHIMMER_3) {
								initializeShimmer3();
							}
							msg = "Shimmer Version (HW) Response Received: " + Arrays.toString(bufferShimmerVersion);
							printLogDataForDebugging(msg);
						} else if (tb[0]==ECG_CALIBRATION_RESPONSE){
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							byte[] bufferCalibrationParameters = new byte[8]; 
							bufferCalibrationParameters = readBytes(4);
															
							mECGCalRawParams = new byte[9];
							System.arraycopy(bufferCalibrationParameters, 0, mECGCalRawParams, 1, 8);
							mECGCalRawParams[0] = ECG_CALIBRATION_RESPONSE;
							//get ecg 
							retrievebiophysicalcalibrationparametersfrompacket( bufferCalibrationParameters,ECG_CALIBRATION_RESPONSE);
							msg = "ECG Calibration Response Received";
							printLogDataForDebugging(msg);
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						} else if (tb[0]==EMG_CALIBRATION_RESPONSE){
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							byte[] bufferCalibrationParameters = new byte[4]; 
							bufferCalibrationParameters = readBytes(4);
							
							mEMGCalRawParams = new byte[5];
							System.arraycopy(bufferCalibrationParameters, 0, mEMGCalRawParams, 1, 4);
							mEMGCalRawParams[0] = EMG_CALIBRATION_RESPONSE;
							//get EMG
							msg = "EMG Calibration Response Received";
							printLogDataForDebugging(msg);
							retrievebiophysicalcalibrationparametersfrompacket( bufferCalibrationParameters,EMG_CALIBRATION_RESPONSE);
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						}
					}
				}
				if (mStreaming==true) {
					tb = readBytes(1);



					//Log.d(mClassName,"Incoming Byte: " + Byte.toString(tb[0])); // can be commented out to watch the incoming bytes
					if (mSync==true) {        //if the stack is full
						if (mWaitForAck==true && (byte)tb[0]==ACK_COMMAND_PROCESSED && byteStack.size()==mPacketSize+1){ //this is to handle acks during mid stream, acks only are received between packets.
							if (mCurrentCommand==SET_BLINK_LED){
								mWaitForAck=false;
								mTransactionCompleted = true;   
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mCurrentLEDStatus=(int)((byte[])mListofInstructions.get(0))[1];
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
						} else { // the first time you start streaming it will go through this piece of code to make sure the data streaming is alligned/sync
							if (byteStack.size()==mPacketSize+1){
								if (tb[0]==DATA_PACKET && byteStack.firstElement()==DATA_PACKET) { //check for the starting zero of the packet, and the starting zero of the subsequent packet, this causes a delay equivalent to the transmission duration between two packets
									newPacket=convertstacktobytearray(byteStack,mPacketSize);
									ObjectCluster objectCluster=new ObjectCluster(mMyName,getBluetoothAddress());
									objectCluster=(ObjectCluster) buildMsg(newPacket, objectCluster);
									//printtofile(newmsg.UncalibratedData);
									dataHandler(objectCluster);
									
									byteStack.clear();
									if (mContinousSync==false) {         //disable continuous synchronizing 
										mSync=false;
									}
								}
							}
							/*if (mStreaming==true && mWaitForAck==true && (byte)tb[0]==ACK_COMMAND_PROCESSED && (packetStack.size()==0)){ //this is to handle acks during mid stream, acks only are received between packets.
                        		Log.d("ShimmerCMD","LED_BLINK_ACK_DETECTED");
                        		mWaitForAck=false;
                        		mCurrentLEDStatus=mTempIntValue;
                    		    mTransactionCompleted = true;
                        	} */
							byteStack.push((tb[0])); //push new sensor data into the stack
							if (byteStack.size()>mPacketSize+1) { //if the stack has reached the packet size remove an element from the stack
								byteStack.removeElementAt(0);
								
							}
						}
					} else if (mSync==false){ //shimmershimmer
						if (mWaitForAck==true && (byte)tb[0]==ACK_COMMAND_PROCESSED && byteStack.size()==0){ //this is to handle acks during mid stream, acks only are received between packets.
							if (mCurrentCommand==SET_BLINK_LED){
							
								mWaitForAck=false;
								mTransactionCompleted = true;   
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mCurrentLEDStatus=(int)((byte[])mListofInstructions.get(0))[1];
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
						} else {
							byteStack.push((tb[0])); //push new sensor data into the stack
							if(byteStack.firstElement()==DATA_PACKET && (byteStack.size()==mPacketSize+1)) {         //only used when continous sync is disabled
								newPacket=convertstacktobytearray(byteStack,mPacketSize);
								ObjectCluster objectCluster=new ObjectCluster(mMyName,getBluetoothAddress());
								objectCluster=(ObjectCluster) buildMsg(newPacket, objectCluster);
								dataHandler(objectCluster);
								byteStack.clear();
							}

							if (byteStack.size()>mPacketSize) { //if the stack has reached the packet size remove an element from the stack
								byteStack.removeElementAt(0);
								
							}
						}
					}




				}
			}
		}
	}
	public void readShimmerVersion() {
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
			mShimmerVersion = SHIMMER_2R; // on Shimmer2r has 
			
		} else if (mFWVersion!=1.2){
			mListofInstructions.add(new byte[]{GET_SHIMMER_VERSION_COMMAND_NEW});
		} else {
			mListofInstructions.add(new byte[]{GET_SHIMMER_VERSION_COMMAND});
		}
	}

	/**
	 * By default once connected no low power modes will be enabled. Low power modes should be enabled post connection once the MSG_STATE_FULLY_INITIALIZED is sent 
	 */
	private void initializeShimmer2R(){ 
		readSamplingRate();
		readMagSamplingRate();
		writeBufferSize(1);
		readBlinkLED();
		readConfigByte0();
		readCalibrationParameters("All");
		if (mSetupDevice==true){
			writeMagRange(mMagGain); //set to default Shimmer mag gain
			writeAccelRange(mAccelRange);
			writeGSRRange(mGSRRange);
			writeSamplingRate(mSamplingRate);	
			writeEnabledSensors(mSetEnabledSensors);
			setContinuousSync(mContinousSync);
		} else {
			if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
			
			} else {
				readMagRange();
			}
			inquiry();
		}
	}


	private void initializeShimmer3(){
		readSamplingRate();
		readMagRange();
		readAccelRange();
		readGyroRange();
		readAccelSamplingRate();
		readCalibrationParameters("All");
		readpressurecalibrationcoefficients();
		//enableLowPowerMag(mLowPowerMag);
		if (mSetupDevice==true){
			//writeAccelRange(mAccelRange);
			writeGSRRange(mGSRRange);
			writeAccelRange(mAccelRange);
			writeGyroRange(mGyroRange);
			writeMagRange(mMagGain);
			writeSamplingRate(mSamplingRate);	
//			setContinuousSync(mContinousSync);
			writeEnabledSensors(mSetEnabledSensors); //this should always be the last command
		} else {
			inquiry();
		}
	}

	/**
	 * writePressureResolution(range) sets the resolution of the pressure sensor on the Shimmer3
	 * @param settinge Numeric value defining the desired resolution of the pressure sensor. Valid range settings are 0 (low), 1 (normal), 2 (high), 3 (ultra high)
	 * 
	 * */
	public void writePressureResolution(int setting) {
		if (mShimmerVersion==SHIMMER_3){
			mListofInstructions.add(new byte[]{SET_BMP180_PRES_RESOLUTION_COMMAND, (byte)setting});
		}
	}

	/**
	 * writeAccelRange(range) sets the Accelerometer range on the Shimmer to the value of the input range. When setting/changing the accel range, please ensure you have the correct calibration parameters. Note that the Shimmer device can only carry one set of accel calibration parameters at a single time.
	 * @param range is a numeric value defining the desired accelerometer range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 */
	public void writeAccelRange(int range) {
		mListofInstructions.add(new byte[]{SET_ACCEL_SENSITIVITY_COMMAND, (byte)range});
		mAccelRange=(int)range;
		
	}

	/**
	 * writeGyroRange(range) sets the Gyroscope range on the Shimmer3 to the value of the input range. When setting/changing the range, please ensure you have the correct calibration parameters.
	 * @param range is a numeric value defining the desired gyroscope range. 
	 */
	public void writeGyroRange(int range) {
		if (mShimmerVersion==SHIMMER_3){
			mListofInstructions.add(new byte[]{SET_MPU9150_GYRO_RANGE_COMMAND, (byte)range});
			mGyroRange=(int)range;
		}
	}

	/**
	 * @param rate Defines the sampling rate to be set (e.g.51.2 sets the sampling rate to 51.2Hz). User should refer to the document Sampling Rate Table to see all possible values.
	 */
	public void writeSamplingRate(double rate) {
		if (mInitialized=true) {

			if (mShimmerVersion==SHIMMER_2 || mShimmerVersion==SHIMMER_2R){
				if (!mLowPowerMag){
					if (rate<=10) {
						writeMagSamplingRate(4);
					} else if (rate<=20) {
						writeMagSamplingRate(5);
					} else {
						writeMagSamplingRate(6);
					}
				} else {
					writeMagSamplingRate(4);
				}
				rate=1024/rate; //the equivalent hex setting
				mListofInstructions.add(new byte[]{SET_SAMPLING_RATE_COMMAND, (byte)Math.rint(rate), 0x00});
			} else if (mShimmerVersion==SHIMMER_3) {
				if (!mLowPowerMag){
					if (rate<=1) {
						writeMagSamplingRate(1);
					} else if (rate<=15) {
						writeMagSamplingRate(4);
					} else if (rate<=30){
						writeMagSamplingRate(5);
					} else if (rate<=75){
						writeMagSamplingRate(6);
					} else {
						writeMagSamplingRate(7);
					}
				} else {
					if (rate >=10){
						writeMagSamplingRate(4);
					} else {
						writeMagSamplingRate(1);
					}
				}

				if (!mLowPowerAccel){
					if (rate<=1){
						writeAccelSamplingRate(1);
					} else if (rate<=10){
						writeAccelSamplingRate(2);
					} else if (rate<=25){
						writeAccelSamplingRate(3);
					} else if (rate<=50){
						writeAccelSamplingRate(4);
					} else if (rate<=100){
						writeAccelSamplingRate(5);
					} else if (rate<=200){
						writeAccelSamplingRate(6);
					} else {
						writeAccelSamplingRate(7);
					}
				}
				else {
					if (rate>=10){
						writeAccelSamplingRate(2);
					} else {
						writeAccelSamplingRate(1);
					}
				}

				if (!mLowPowerGyro){
					if (rate<=51.28){
						writeGyroSamplingRate(0x9B);
					} else if (rate<=102.56){
						writeGyroSamplingRate(0x4D);
					} else if (rate<=129.03){
						writeGyroSamplingRate(0x3D);
					} else if (rate<=173.91){
						writeGyroSamplingRate(0x2D);
					} else if (rate<=205.13){
						writeGyroSamplingRate(0x26);
					} else if (rate<=258.06){
						writeGyroSamplingRate(0x1E);
					} else if (rate<=533.33){
						writeGyroSamplingRate(0xE);
					} else {
						writeGyroSamplingRate(6);
					}
				}
				else {
					writeGyroSamplingRate(0xFF);
				}



				int samplingByteValue = (int) (32768/rate);
				mListofInstructions.add(new byte[]{SET_SAMPLING_RATE_COMMAND, (byte)(samplingByteValue&0xFF), (byte)((samplingByteValue>>8)&0xFF)});




			}
		}
	}

	/**
	 * This returns the variable mTransactionCompleted which indicates whether the Shimmer device is in the midst of a command transaction. True when no transaction is taking place. This is deprecated since the update to a thread model for executing commands
	 * @return mTransactionCompleted
	 */
	public boolean getInstructionStatus()
	{	
		boolean instructionStatus=false;
		if (mTransactionCompleted == true) {
			instructionStatus=true;
		} else {
			instructionStatus=false;
		}
		return instructionStatus;
	}
	
	private void enableLowResolutionMode(boolean enable){
		while(getInstructionStatus()==false) {};
		if (mFWVersion==0.1 && mFWInternal==0){

		} else if (mShimmerVersion == SHIMMER_3){
			if (enable){
				mListofInstructions.add(new byte[]{SET_LSM303DLHC_ACCEL_LPMODE_COMMAND, (byte)0x01});
				mListofInstructions.add(new byte[]{SET_LSM303DLHC_ACCEL_HRMODE_COMMAND, (byte)0x00});

			} else {
				mListofInstructions.add(new byte[]{SET_LSM303DLHC_ACCEL_HRMODE_COMMAND, (byte)0x01});
				mListofInstructions.add(new byte[]{SET_LSM303DLHC_ACCEL_LPMODE_COMMAND, (byte)0x00});

			}

		}
	}



	/**
	 * writeMagSamplingRate(range) sets the MagSamplingRate on the Shimmer to the value of the input range.
	 * @param rate for Shimmer 2 it is a value between 1 and 6; 0 = 0.5 Hz; 1 = 1.0 Hz; 2 = 2.0 Hz; 3 = 5.0 Hz; 4 = 10.0 Hz; 5 = 20.0 Hz; 6 = 50.0 Hz, for Shimmer 3 it is a value between 0-7; 0 = 0.75Hz; 1 = 1.5Hz; 2 = 3Hz; 3 = 7.5Hz; 4 = 15Hz ; 5 = 30 Hz; 6 = 75Hz ; 7 = 220Hz 
	 * 
	 * */
	private void writeMagSamplingRate(int rate) {
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mTempIntValue=rate;
			mListofInstructions.add(new byte[]{SET_MAG_SAMPLING_RATE_COMMAND, (byte)rate});
		}
	}

	/**
	 * This enables the calculation of 3D orientation through the use of the gradient descent algorithm, note that the user will have to ensure that mEnableCalibration has been set to true (see enableCalibration), and that the accel, gyro and mag has been enabled
	 * @param enable
	 */
	public void enable3DOrientation(boolean enable){
		//enable the sensors if they have not been enabled 
		mOrientationEnabled = enable;
	}

	
	/**
	 * writeAccelSamplingRate(range) sets the AccelSamplingRate on the Shimmer (version 3) to the value of the input range.
	 * @param rate it is a value between 1 and 7; 1 = 1 Hz; 2 = 10 Hz; 3 = 25 Hz; 4 = 50 Hz; 5 = 100 Hz; 6 = 200 Hz; 7 = 400 Hz
	 */
	private void writeAccelSamplingRate(int rate) {
		if (mShimmerVersion == SHIMMER_3){
			mTempIntValue=rate;
			mListofInstructions.add(new byte[]{SET_ACCEL_SAMPLING_RATE_COMMAND, (byte)rate});
		}
	}

	/**
	 * writeGyroSamplingRate(range) sets the GyroSamplingRate on the Shimmer (version 3) to the value of the input range.
	 * @param rate it is a value between 0 and 255; 6 = 1152Hz, 77 = 102.56Hz, 255 = 31.25Hz
	 */
	private void writeGyroSamplingRate(int rate) {
		if (mShimmerVersion == SHIMMER_3){
			mTempIntValue=rate;
			mListofInstructions.add(new byte[]{SET_MPU9150_SAMPLING_RATE_COMMAND, (byte)rate});
		}
	}

	/**
	 * This enables the low power accel option. When not enabled the sampling rate of the accel is set to the closest value to the actual sampling rate that it can achieve. In low power mode it defaults to 10Hz. Also and additional low power mode is used for the LSM303DLHC. This command will only supports the following Accel range +4g, +8g , +16g 
	 * @param enable
	 */
	public void enableLowPowerAccel(boolean enable){
		mLowPowerAccel = enable;
		if (!mLowPowerAccel){
			enableLowResolutionMode(false);
			if (mSamplingRate<=1){
				writeAccelSamplingRate(1);
			} else if (mSamplingRate<=10){
				writeAccelSamplingRate(2);
			} else if (mSamplingRate<=25){
				writeAccelSamplingRate(3);
			} else if (mSamplingRate<=50){
				writeAccelSamplingRate(4);
			} else if (mSamplingRate<=100){
				writeAccelSamplingRate(5);
			} else if (mSamplingRate<=200){
				writeAccelSamplingRate(6);
			} else {
				writeAccelSamplingRate(7);
			}
		}
		else {
			enableLowResolutionMode(true);
			writeAccelSamplingRate(2);
		}
	}

	/**
	 * This enables the low power accel option. When not enabled the sampling rate of the accel is set to the closest value to the actual sampling rate that it can achieve. In low power mode it defaults to 10Hz. Also and additional low power mode is used for the LSM303DLHC. This command will only supports the following Accel range +4g, +8g , +16g 
	 * @param enable
	 */
	public void enableLowPowerGyro(boolean enable){
		mLowPowerGyro = enable;
		if (!mLowPowerGyro){
			if (mSamplingRate<=51.28) {
				writeGyroSamplingRate(0x9B);
			} else if (mSamplingRate<=102.56) {
				writeGyroSamplingRate(0x4D);
			} else if (mSamplingRate<=129.03) {
				writeGyroSamplingRate(0x3D);
			} else if (mSamplingRate<=173.91) {
				writeGyroSamplingRate(0x2D);
			} else if (mSamplingRate<=205.13) {
				writeGyroSamplingRate(0x26);
			} else if (mSamplingRate<=258.06) {
				writeGyroSamplingRate(0x1E);
			} else if (mSamplingRate<=533.33) {
				writeGyroSamplingRate(0xE);
			} else {
				writeGyroSamplingRate(6);
			}
		}
		else {
			writeGyroSamplingRate(0xFF);
		}
	}


	/**
	 * Transmits a command to the Shimmer device to enable the sensors. To enable multiple sensors an or operator should be used (e.g. writeEnabledSensors(SENSOR_ACCEL|SENSOR_GYRO|SENSOR_MAG)). Command should not be used consecutively. Valid values are SENSOR_ACCEL, SENSOR_GYRO, SENSOR_MAG, SENSOR_ECG, SENSOR_EMG, SENSOR_GSR, SENSOR_EXP_BOARD_A7, SENSOR_EXP_BOARD_A0, SENSOR_STRAIN and SENSOR_HEART.
    SENSOR_BATT
	 * @param enabledSensors e.g SENSOR_ACCEL|SENSOR_GYRO|SENSOR_MAG
	 */
	public void writeEnabledSensors(int enabledSensors) {
		if (!sensorConflictCheck(enabledSensors)){ //sensor conflict check
		
		} else {
			enabledSensors=generateSensorBitmapForHardwareControl(enabledSensors);
			tempEnabledSensors=enabledSensors;
			byte secondByte=(byte)((enabledSensors & 65280)>>8);
			byte firstByte=(byte)(enabledSensors & 0xFF);
			//write(new byte[]{SET_SENSORS_COMMAND,(byte) lowByte, highByte});
			if (mShimmerVersion == SHIMMER_3){
				byte thirdByte=(byte)((enabledSensors & 16711680)>>16);
				mListofInstructions.add(new byte[]{SET_SENSORS_COMMAND,(byte) firstByte,(byte) secondByte,(byte) thirdByte});
			} else {
				mListofInstructions.add(new byte[]{SET_SENSORS_COMMAND,(byte) firstByte,(byte) secondByte});
			}
			inquiry();
		}
	}


	/**
	 * writeGSRRange(range) sets the GSR range on the Shimmer to the value of the input range. 
	 * @param range numeric value defining the desired GSR range. Valid range settings are 0 (10kOhm to 56kOhm), 1 (56kOhm to 220kOhm), 2 (220kOhm to 680kOhm), 3 (680kOhm to 4.7MOhm) and 4 (Auto Range).
	 */
	public void writeGSRRange(int range) {
		if (mShimmerVersion == SHIMMER_3){
			if (mFWVersion!=0.1 || mFWInternal >4){
				mListofInstructions.add(new byte[]{SET_GSR_RANGE_COMMAND, (byte)range});
			}
		} else {
			mListofInstructions.add(new byte[]{SET_GSR_RANGE_COMMAND, (byte)range});
		}
	}
	public void readpressurecalibrationcoefficients() {
		if (mShimmerVersion == SHIMMER_3){
			mListofInstructions.add(new byte[]{ GET_BMP180_CALIBRATION_COEFFICIENTS_COMMAND});
		}
	}

	
	/**
	 * @param sensor is a string value that defines the sensor. Accepted sensor values are "Accelerometer","Gyroscope","Magnetometer","ECG","EMG","All"
	 */
	public void readCalibrationParameters(String sensor) {
	
			if (!mInitialized){
				if (mFWVersion==0.1 && mFWInternal==0  && mShimmerVersion!=3) {
					//mFWVersionFullName="BoilerPlate 0.1.0";
					/*Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
          	        Bundle bundle = new Bundle();
          	        bundle.putString(TOAST, "Firmware Version: " +mFWVersionFullName);
          	        msg.setData(bundle);
          	        mHandler.sendMessage(msg);*/
				}	
			}
			if (sensor.equals("Accelerometer")) {
				mListofInstructions.add(new byte[]{GET_ACCEL_CALIBRATION_COMMAND});
			}
			else if (sensor.equals("Gyroscope")) {
				mListofInstructions.add(new byte[]{GET_GYRO_CALIBRATION_COMMAND});
			}
			else if (sensor.equals("Magnetometer")) {
				mListofInstructions.add(new byte[]{GET_MAG_CALIBRATION_COMMAND});
			}
			else if (sensor.equals("All")){
				mListofInstructions.add(new byte[]{GET_ALL_CALIBRATION_COMMAND});
			} 
			else if (sensor.equals("ECG")){
				mListofInstructions.add(new byte[]{GET_ECG_CALIBRATION_COMMAND});
			} 
			else if (sensor.equals("EMG")){
				mListofInstructions.add(new byte[]{GET_EMG_CALIBRATION_COMMAND});
			}
		
	}
	
	public void readSamplingRate() {
		mListofInstructions.add(new byte[]{GET_SAMPLING_RATE_COMMAND});
	}



	/**
	 * An inquiry is used to request for the current configuration parameters from the Shimmer device (e.g. Accelerometer settings, Configuration Byte, Sampling Rate, Number of Enabled Sensors and Sensors which have been enabled). 
	 */
	public void inquiry() {
		mListofInstructions.add(new byte[]{INQUIRY_COMMAND});
	}


	/**
	 * writeMagRange(range) sets the MagSamplingRate on the Shimmer to the value of the input range. When setting/changing the accel range, please ensure you have the correct calibration parameters. Note that the Shimmer device can only carry one set of accel calibration parameters at a single time.
	 * @param range is the mag rang
	 */
	public void writeMagRange(int range) {
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mListofInstructions.add(new byte[]{SET_MAG_GAIN_COMMAND, (byte)range});
		}
	}


	public void writeLEDCommand(int command) {
		if (mShimmerVersion!=SHIMMER_3){
			if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
			} else {
				mListofInstructions.add(new byte[]{SET_BLINK_LED, (byte)command});
			}
		}
	}

	/*public void writeGyroTempVref(int value){

    }*/



	public void writeECGCalibrationParameters(int offsetrall, int gainrall,int offsetlall, int gainlall) {
		byte[] data = new byte[8];
		data[0] = (byte) ((offsetlall>>8)& 0xFF); //MSB offset
		data[1] = (byte) ((offsetlall)& 0xFF);
		data[2] = (byte) ((gainlall>>8)& 0xFF); //MSB gain
		data[3] = (byte) ((gainlall)& 0xFF);
		data[4] = (byte) ((offsetrall>>8)& 0xFF); //MSB offset
		data[5] = (byte) ((offsetrall)& 0xFF);
		data[6] = (byte) ((gainrall>>8)& 0xFF); //MSB gain
		data[7] = (byte) ((gainrall)& 0xFF);
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mListofInstructions.add(new byte[]{SET_ECG_CALIBRATION_COMMAND,data[0],data[1],data[2],data[3],data[4],data[5],data[6],data[7]});
		}
	}

	public void writeEMGCalibrationParameters(int offset, int gain) {
		byte[] data = new byte[4];
		data[0] = (byte) ((offset>>8)& 0xFF); //MSB offset
		data[1] = (byte) ((offset)& 0xFF);
		data[2] = (byte) ((gain>>8)& 0xFF); //MSB gain
		data[3] = (byte) ((gain)& 0xFF);
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mListofInstructions.add(new byte[]{SET_EMG_CALIBRATION_COMMAND,data[0],data[1],data[2],data[3]});
		}
	}

	public void readGSRRange() {
		mListofInstructions.add(new byte[]{GET_GSR_RANGE_COMMAND});
	}

	public void readAccelRange() {
		mListofInstructions.add(new byte[]{GET_ACCEL_SENSITIVITY_COMMAND});
	}

	public void readGyroRange() {
		mListofInstructions.add(new byte[]{GET_MPU9150_GYRO_RANGE_COMMAND});
	}

	public void readBufferSize() {
		mListofInstructions.add(new byte[]{GET_BUFFER_SIZE_COMMAND});
	}

	public void readMagSamplingRate() {
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mListofInstructions.add(new byte[]{GET_MAG_SAMPLING_RATE_COMMAND});
		}
	}

	/**
	 * Used to retrieve the data rate of the Accelerometer on Shimmer 3
	 */
	public void readAccelSamplingRate() {
		if (mShimmerVersion!=3){
		} else {
			mListofInstructions.add(new byte[]{GET_ACCEL_SAMPLING_RATE_COMMAND});
		}
	}


	public void readMagRange() {
		mListofInstructions.add(new byte[]{GET_MAG_GAIN_COMMAND});
	}

	public void readBlinkLED() {
		mListofInstructions.add(new byte[]{GET_BLINK_LED});
	}

	

	public void readECGCalibrationParameters() {
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mListofInstructions.add(new byte[]{GET_ECG_CALIBRATION_COMMAND});
		}
	}

	public void readEMGCalibrationParameters() {
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mListofInstructions.add(new byte[]{GET_EMG_CALIBRATION_COMMAND});
		}
	}

	

	public void readConfigByte0() {
		mListofInstructions.add(new byte[]{GET_CONFIG_BYTE0_COMMAND});
	}

	/**
	 * writeConfigByte0(configByte0) sets the config byte0 value on the Shimmer to the value of the input configByte0. 
	 * @param configByte0 is an unsigned 8 bit value defining the desired config byte 0 value.
	 */
	public void writeConfigByte0(byte configByte0) {
		mListofInstructions.add(new byte[]{SET_CONFIG_BYTE0_COMMAND,(byte) configByte0});
	}

	/**
	 * @param enabledSensors This takes in the current list of enabled sensors 
	 * @param sensorToCheck This takes in a single sensor which is to be enabled
	 * @return enabledSensors This returns the new set of enabled sensors, where any sensors which conflicts with sensorToCheck is disabled on the bitmap, so sensorToCheck can be accomodated (e.g. for Shimmer2 using ECG will disable EMG,GSR,..basically any daughter board)
	 *  
	 */
	public int sensorConflictCheckandCorrection(int enabledSensors,int sensorToCheck){

		if (mShimmerVersion==SHIMMER_2R || mShimmerVersion==SHIMMER_2){
			if ((sensorToCheck & SENSOR_GYRO) >0 || (sensorToCheck & SENSOR_MAG) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_ECG);
				enabledSensors = disableBit(enabledSensors,SENSOR_EMG);
				enabledSensors = disableBit(enabledSensors,SENSOR_GSR);
				enabledSensors = disableBit(enabledSensors,SENSOR_STRAIN);
			} else if ((sensorToCheck & SENSOR_STRAIN) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_ECG);
				enabledSensors = disableBit(enabledSensors,SENSOR_EMG);
				enabledSensors = disableBit(enabledSensors,SENSOR_GSR);
				enabledSensors = disableBit(enabledSensors,SENSOR_GYRO);
				enabledSensors = disableBit(enabledSensors,SENSOR_MAG);
			} else if ((sensorToCheck & SENSOR_GSR) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_ECG);
				enabledSensors = disableBit(enabledSensors,SENSOR_EMG);
				enabledSensors = disableBit(enabledSensors,SENSOR_STRAIN);
				enabledSensors = disableBit(enabledSensors,SENSOR_GYRO);
				enabledSensors = disableBit(enabledSensors,SENSOR_MAG);
			} else if ((sensorToCheck & SENSOR_ECG) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_GSR);
				enabledSensors = disableBit(enabledSensors,SENSOR_EMG);
				enabledSensors = disableBit(enabledSensors,SENSOR_STRAIN);
				enabledSensors = disableBit(enabledSensors,SENSOR_GYRO);
				enabledSensors = disableBit(enabledSensors,SENSOR_MAG);
			} else if ((sensorToCheck & SENSOR_EMG) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_GSR);
				enabledSensors = disableBit(enabledSensors,SENSOR_ECG);
				enabledSensors = disableBit(enabledSensors,SENSOR_STRAIN);
				enabledSensors = disableBit(enabledSensors,SENSOR_GYRO);
				enabledSensors = disableBit(enabledSensors,SENSOR_MAG);
			} else if ((sensorToCheck & SENSOR_HEART) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_EXP_BOARD_A0);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXP_BOARD_A7);
			} else if ((sensorToCheck & SENSOR_EXP_BOARD_A0) >0 || (sensorToCheck & SENSOR_EXP_BOARD_A7) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_HEART);
				enabledSensors = disableBit(enabledSensors,SENSOR_BATT);
			} else if ((sensorToCheck & SENSOR_BATT) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_EXP_BOARD_A0);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXP_BOARD_A7);
			}
		}
		enabledSensors = enabledSensors ^ sensorToCheck;
		return enabledSensors;
	}

	private int disableBit(int number,int disablebitvalue){
		if ((number&disablebitvalue)>0){
			number = number ^ disablebitvalue;
		}
		return number;
	}
	public boolean sensorConflictCheck(int enabledSensors){
		boolean pass=true;
		if (mShimmerVersion != SHIMMER_3){
			if (((enabledSensors & 0xFF)& SENSOR_GYRO) > 0){
				if (((enabledSensors & 0xFF)& SENSOR_EMG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_ECG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_GSR) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF00) & SENSOR_STRAIN) > 0){
					pass=false;
				}
			}

			if (((enabledSensors & 0xFF)& SENSOR_MAG) > 0){
				if (((enabledSensors & 0xFF)& SENSOR_EMG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_ECG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_GSR) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF00) & SENSOR_STRAIN) > 0){
					pass=false;
				}
			}

			if (((enabledSensors & 0xFF)& SENSOR_EMG) > 0){
				if (((enabledSensors & 0xFF)& SENSOR_GYRO) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_MAG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_ECG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_GSR) > 0){
					pass=false;
				}else if (((enabledSensors & 0xFF00) & SENSOR_STRAIN) > 0){
					pass=false;
				}
			}

			if (((enabledSensors & 0xFF)& SENSOR_ECG) > 0){
				if (((enabledSensors & 0xFF)& SENSOR_GYRO) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_MAG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_EMG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_GSR) > 0){
					pass=false;
				}else if (((enabledSensors & 0xFF00) & SENSOR_STRAIN) > 0){
					pass=false;
				}
			}

			if (((enabledSensors & 0xFF)& SENSOR_GSR) > 0){
				if (((enabledSensors & 0xFF)& SENSOR_GYRO) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_MAG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_EMG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_ECG) > 0){
					pass=false;
				}else if (((enabledSensors & 0xFF00) & SENSOR_STRAIN) > 0){
					pass=false;
				}
			}

			if (((enabledSensors & 0xFF00) & SENSOR_STRAIN) > 0){
				if (((enabledSensors & 0xFF)& SENSOR_GYRO) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_MAG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_EMG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_ECG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_GSR) > 0){
					pass=false;
				} else if (get5VReg()==1){ // if the 5volt reg is set 
					pass=false;
				}
			}

			if (((enabledSensors & 0xFF) & SENSOR_EXP_BOARD_A0) > 0) {
				if (((enabledSensors & 0xFFFFF) & SENSOR_BATT) > 0) {
					pass=false;
				} else if (getPMux()==1){
					
					writePMux(0);
				}
			}

			if (((enabledSensors & 0xFF) & SENSOR_EXP_BOARD_A7) > 0) {
				if (((enabledSensors & 0xFFFFF) & SENSOR_BATT) > 0) {
					pass=false;
				}else if (getPMux()==1){
					writePMux(0);
				}
			}

			if (((enabledSensors & 0xFFFFF) & SENSOR_BATT) > 0) {
				if (((enabledSensors & 0xFF) & SENSOR_EXP_BOARD_A7) > 0){
					pass=false;
				} 
				if (((enabledSensors & 0xFF) & SENSOR_EXP_BOARD_A0) > 0){
					pass=false;
				}
				if (((enabledSensors & 0xFFFFF) & SENSOR_BATT) > 0){
					if (getPMux()==0){
						
						writePMux(1);
					}
				}
			}
			if (!pass){
				
			}
		}
		return pass;
	}
	

	/**
	 * @param enabledSensors this bitmap is only applicable for the instrument driver and does not correspond with the values in the firmware
	 * @return enabledSensorsFirmware returns the bitmap for the firmware
	 * The reason for this is hardware and firmware change may eventually need a different sensor bitmap, to keep the ID forward compatible, this function is used. The ID has its own seperate sensor bitmap.
	 */
	private int generateSensorBitmapForHardwareControl(int enabledSensors){
		int hardwareSensorBitmap=0;

		//check if the batt volt is enabled (this is only applicable for Shimmer_2R
		if (mShimmerVersion == SHIMMER_2R || mShimmerVersion == SHIMMER_2){
			if (((enabledSensors & 0xFFFFF) & SENSOR_BATT) > 0 ){
				enabledSensors = enabledSensors & 0xFFFF;
				enabledSensors = enabledSensors|SENSOR_EXP_BOARD_A0|SENSOR_EXP_BOARD_A7;
			}
			hardwareSensorBitmap  = enabledSensors;
		} else if (mShimmerVersion == SHIMMER_3){
			if (((enabledSensors & 0xFF)& SENSOR_ACCEL) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_A_ACCEL_S3;
			}
			if ((enabledSensors & SENSOR_DACCEL) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL_S3;
			}
			
			if (((enabledSensors & 0xFF)& SENSOR_GYRO) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO_S3;
			}
			if (((enabledSensors & 0xFF)& SENSOR_MAG) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_MAG_S3;
			}
			if ((enabledSensors & SENSOR_BATT) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_VBATT_S3;
			}
			if ((enabledSensors & SENSOR_EXT_ADC_A7) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_EXT_A7;
			}
			if ((enabledSensors & SENSOR_EXT_ADC_A6) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_EXT_A6;
			}
			if ((enabledSensors & SENSOR_EXT_ADC_A15) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_EXT_A15;
			}
			if ((enabledSensors & SENSOR_INT_ADC_A1) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_INT_A1;
			}
			if ((enabledSensors & SENSOR_INT_ADC_A12) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_INT_A12;
			}
			if ((enabledSensors & SENSOR_INT_ADC_A13) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_INT_A13;
			}
			if ((enabledSensors & SENSOR_INT_ADC_A14) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_INT_A14;
			}
			if  ((enabledSensors & SENSOR_BMP180) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_BMP180;
			} 
			if ((enabledSensors & SENSOR_GSR) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap| Configuration.Shimmer3.SensorBitmap.SENSOR_GSR;
			} 
		} else { 
			hardwareSensorBitmap  = enabledSensors;
		}

		return hardwareSensorBitmap;
	}

	/*
	 * Set and Get Methods
	 * */    
	public void setContinuousSync(boolean continousSync){
		mContinousSync=continousSync;
	}
	

	/**
	 * writeAccelRange(range) sets the Accelerometer range on the Shimmer to the value of the input range. When setting/changing the accel range, please ensure you have the correct calibration parameters. Note that the Shimmer device can only carry one set of accel calibration parameters at a single time.
	 * @param range is a numeric value defining the desired accelerometer range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 */
	public void writeBufferSize(int size) {
		mListofInstructions.add(new byte[]{SET_BUFFER_SIZE_COMMAND, (byte)size});
	}


	public void stopStreaming() {
		mListofInstructions.add(new byte[]{STOP_STREAMING_COMMAND});
		mCurrentLEDStatus=-1;
	}

	public void startStreaming() {
		mPacketLossCount = 0;
		mPacketReceptionRate = 100;
		mFirstTimeCalTime=true;
		mLastReceivedCalibratedTimeStamp = -1;
		mSync=true; // a backup sync done every time you start streaming
		mListofInstructions.add(new byte[]{START_STREAMING_COMMAND});
	}

	protected synchronized void initialize() {	    	//See two constructors for Shimmer
		//InstructionsThread instructionsThread = new InstructionsThread();
		//instructionsThread.start();
		dummyreadSamplingRate(); // it actually acts to clear the write buffer
		readFWVersion();
		//mShimmerVersion=4;

	}
	
	private byte[] convertstacktobytearray(Stack<Byte> b,int packetSize) {
		byte[] returnByte=new byte[packetSize];
		b.remove(0); //remove the Data Packet identifier 
		for (int i=0;i<packetSize;i++) {
			returnByte[packetSize-1-i]=(byte) b.pop();
		}
		return returnByte;
	}
	
	public void readFWVersion() {
		mDummy=false;//false
		mListofInstructions.add(new byte[]{GET_FW_VERSION_COMMAND});
	}



	/**
	 * The reason for this is because sometimes the 1st response is not received by the phone
	 */
	protected void dummyreadSamplingRate() {
		mDummy=true;
		mListofInstructions.add(new byte[]{GET_SAMPLING_RATE_COMMAND});
	}

	/**
	 * This enables the low power mag option. When not enabled the sampling rate of the mag is set to the closest value to the actual sampling rate that it can achieve. In low power mode it defaults to 10Hz
	 * @param enable
	 */
	public void enableLowPowerMag(boolean enable){
		mLowPowerMag = enable;
		if (mShimmerVersion!=SHIMMER_3){
			if (!mLowPowerMag){
				if (mSamplingRate>=50){
					writeMagSamplingRate(6);
				} else if (mSamplingRate>=20) {
					writeMagSamplingRate(5);
				} else if (mSamplingRate>=10) {
					writeMagSamplingRate(4);
				} else {
					writeMagSamplingRate(3);
				}
			} else {
				writeMagSamplingRate(4);
			}
		} else {
			if (!mLowPowerMag){
				if (mSamplingRate<=1){
					writeMagSamplingRate(1);
				} else if (mSamplingRate<=15) {
					writeMagSamplingRate(4);
				} else if (mSamplingRate<=30) {
					writeMagSamplingRate(5);
				} else if (mSamplingRate<=75) {
					writeMagSamplingRate(6);
				} else {
					writeMagSamplingRate(7);
				}
			} else {
				if (mSamplingRate>=10){
					writeMagSamplingRate(4);
				} else {
					writeMagSamplingRate(1);
				}
			}
		}
	}
	


	public boolean isLowPowerMagEnabled(){
		return mLowPowerMag;
	}






	public boolean isGyroOnTheFlyCalEnabled(){
		return mEnableOntheFlyGyroOVCal;
	}

	public boolean is3DOrientatioEnabled(){
		return mOrientationEnabled;
	}
	
	


	public boolean isLowPowerAccelEnabled() {
		// TODO Auto-generated method stub
		return mLowPowerAccel;
	}

	public boolean isLowPowerGyroEnabled() {
		// TODO Auto-generated method stub
		return mLowPowerGyro;
	}

	public int getLowPowerAccelEnabled() {
		// TODO Auto-generated method stub
		if ( mLowPowerAccel)
			return 1;
		else
			return 0;
	}

	public int getLowPowerGyroEnabled() {
		// TODO Auto-generated method stub
		if ( mLowPowerGyro)
			return 1;
		else
			return 0;
	}

	public int getLowPowerMagEnabled() {
		// TODO Auto-generated method stub
		if ( mLowPowerMag)
			return 1;
		else
			return 0;
	}



	
	public int getPacketSize(){
		return mPacketSize;
	}


	
	public boolean isUsingDefaultLNAccelParam(){
		return mDefaultCalibrationParametersAccel;
	}
	
	public boolean isUsingDefaultAccelParam(){
		return mDefaultCalibrationParametersAccel;
	}
	public boolean isUsingDefaultWRAccelParam(){
		return mDefaultCalibrationParametersDigitalAccel; 
	}

	public boolean isUsingDefaultGyroParam(){
		return mDefaultCalibrationParametersGyro;
	}
	public boolean isUsingDefaultMagParam(){
		return mDefaultCalibrationParametersMag;
	}
	public boolean isUsingDefaultECGParam(){
		return mDefaultCalibrationParametersECG;
	}
	public boolean isUsingDefaultEMGParam(){
		return mDefaultCalibrationParametersEMG;
	}

	public void resetCalibratedTimeStamp(){
		mLastReceivedCalibratedTimeStamp = -1;
		mFirstTimeCalTime = true;
		mCurrentTimeStampCycle = 0;
	}



	/**
	 * Sets the Pmux bit value on the Shimmer to the value of the input SETBIT. The PMux bit is the 2nd MSB of config byte0.
	 * @param setBit value defining the desired setting of the PMux (1=ON, 0=OFF).
	 */
	public void writePMux(int setBit) {
		mListofInstructions.add(new byte[]{SET_PMUX_COMMAND,(byte) setBit});
	}

	/**
	 * Sets the configGyroTempVref bit value on the Shimmer to the value of the input SETBIT. The configGyroTempVref bit is the 2nd MSB of config byte0.
	 * @param setBit value defining the desired setting of the Gyro Vref (1=ON, 0=OFF).
	 */
	/*public void writeConfigGyroTempVref(int setBit) {
    	while(getInstructionStatus()==false) {};
			//Bit value defining the desired setting of the PMux (1=ON, 0=OFF).
			if (setBit==1) {
				mTempByteValue=(byte) (mConfigByte0|32); 
			} else if (setBit==0) {
				mTempByteValue=(byte)(mConfigByte0 & 223);
			}
			mCurrentCommand=SET_GYRO_TEMP_VREF_COMMAND;
			write(new byte[]{SET_GYRO_TEMP_VREF_COMMAND,(byte) setBit});
			mWaitForAck=true;
			mTransactionCompleted=false;
			responseTimer(ACK_TIMER_DURATION);
	}*/

	/**
	 * Enable/disable the Internal Exp Power on the Shimmer3
	 * @param setBit value defining the desired setting of the Volt regulator (1=ENABLED, 0=DISABLED).
	 */
	public void writeInternalExpPower(int setBit) {
		if (mShimmerVersion == SHIMMER_3 && mFWVersion>=0.2){
			mListofInstructions.add(new byte[]{SET_INTERNAL_EXP_POWER_ENABLE_COMMAND,(byte) setBit});
		} else {
			
		}
	}
	
	
	/**
	 * Enable/disable the 5 Volt Regulator on the Shimmer ExpBoard board
	 * @param setBit value defining the desired setting of the Volt regulator (1=ENABLED, 0=DISABLED).
	 */
	public void writeFiveVoltReg(int setBit) {
		mListofInstructions.add(new byte[]{SET_5V_REGULATOR_COMMAND,(byte) setBit});
	}
	
	public void toggleLed() {
		mListofInstructions.add(new byte[]{TOGGLE_LED_COMMAND});
	}

	

	public boolean getInitialized(){
		return mInitialized;
	}

	public double getPacketReceptionRate(){
		return mPacketReceptionRate;
	}

	

	public int get5VReg(){
		if ((mConfigByte0 & (byte)128)!=0) {
			//then set ConfigByte0 at bit position 7
			return 1;
		} else {
			return 0;
		}
	}

	public int getCurrentLEDStatus() {
		return mCurrentLEDStatus;
	}

	public double getFirmwareVersion(){
		return mFWVersion;
	}
	
	protected void checkBattery(){
		if (!mWaitForAck) {	
			if (mVSenseBattMA.getMean()<mLowBattLimit*1000) {
				if (mCurrentLEDStatus!=1) {
					writeLEDCommand(1);
				}
			} else if(mVSenseBattMA.getMean()>mLowBattLimit*1000+100) { //+100 is to make sure the limits are different to prevent excessive switching when the batt value is at the threshold
				if (mCurrentLEDStatus!=0) {
					writeLEDCommand(0);
				}
			}

		}
	
	}
	

	/**
	 * Set the battery voltage limit, when the Shimmer device goes below the limit while streaming the LED on the Shimmer device will turn Yellow, in order to use battery voltage monitoring the Battery has to be enabled. See writeenabledsensors. Only to be used with Shimmer2. Calibration also has to be enabled, see enableCalibration.
	 * @param limit
	 */
	public void setBattLimitWarning(double limit){
		mLowBattLimit=limit;
	}

	public double getBattLimitWarning(){
		return mLowBattLimit;
	}

	public int getShimmerVersion(){
		return mShimmerVersion;
	}

	public String getShimmerName(){
		return mMyName;
	}

	


}
