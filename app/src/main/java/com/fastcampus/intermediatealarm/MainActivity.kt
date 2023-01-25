package com.fastcampus.intermediatealarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.service.autofill.Validators.and
import android.widget.Button
import android.widget.TextView
import java.util.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //TODO step0 뷰 초기화하기
        initOnOffButton()
        initChangeAlarmTimeButton()
        //TODO step1 데이터 가져오기
        val model = fetchDataFromSharedPreferences()
        //TODO step2 뷰에 데이터 그려주기
        renderView(model)
    }

    private fun initOnOffButton() {
        val onOffButton = findViewById<Button>(R.id.onoffButton)
        onOffButton.setOnClickListener {
            //TODO 데이터를 확인한다.
            val model = it.tag as? AlarmDisplayModel ?: return@setOnClickListener //as : 형변환에 사용함
            val newModel = saveAlarmModel(model.hour, model.minute, model.onOff.not()) //not() 값 반전
            renderView(newModel)

            //TODO 온 오프에 따라 작업을 처리한다
            if (newModel.onOff) {
                //켜진경우 -> 알람 등록
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, newModel.hour)
                    set(Calendar.MINUTE, newModel.minute)

                    if (before(Calendar.getInstance())) {
                        add(Calendar.DATE, 1)
                    }
                }

                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(this@MainActivity, AlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(this@MainActivity, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT)

                alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pendingIntent)
            } else {
                //꺼진경우 -> 알람 제거
                cancelAlarm()
            }

            //TODO 그 후 데이터 저장
        }
    }

    private fun initChangeAlarmTimeButton() {
        val changeAlarmButton = findViewById<Button>(R.id.changeAlarmTimeButton)
        changeAlarmButton.setOnClickListener {

            //TODO 현재 시간을 가져온다 -> 그 부분을 timepickerDialog show 하는 부분에서 적용함
            val calender = Calendar.getInstance()
            //TODO TimePickDialog을 띄워서 시간을 설정함
            TimePickerDialog(this, { picker, hour, minute ->

                //TODO 그 시간 데이터를 가져와서 저장한다.
                val model = saveAlarmModel(hour, minute, false)
                //TODO 뷰를 업데이트 한다.
                renderView(model)
                //TODO 기존 알람을 제거한다.
                cancelAlarm()

            }, calender.get(Calendar.HOUR_OF_DAY), calender.get(Calendar.MINUTE), false).show()
        }
    }

    private fun saveAlarmModel(hour: Int, minute: Int, onOff: Boolean): AlarmDisplayModel {
        val model = AlarmDisplayModel(
            hour = hour,
            minute = minute,
            onOff = onOff
        )

        val sharedPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE)

        with(sharedPreferences.edit()) {
            putString(ALARM_KEY, model.makeDataForDB())
            putBoolean(ONOFF_KEY, model.onOff)
            commit()
        }

        return model
    }

    private fun fetchDataFromSharedPreferences(): AlarmDisplayModel {
        val sharedPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE)

        val timeDBValue = sharedPreferences.getString(ALARM_KEY, "10:30") ?: "10:30"
        val onOffDBValue = sharedPreferences.getBoolean(ONOFF_KEY, false)
        var alarmData = timeDBValue.split(":")

        val alarmModel = AlarmDisplayModel(
            hour = alarmData[0].toInt(),
            minute = alarmData[1].toInt(),
            onOff = onOffDBValue
        )

        //보정 - 앱에서 보이는 설정과 shared의 onoff값을 확인하여 정확히 설정하는 부분이 필요함
        val pendingIntent = PendingIntent.getBroadcast(this, ALARM_REQUEST_CODE, Intent(this, AlarmReceiver::class.java), PendingIntent.FLAG_NO_CREATE)
        if ((pendingIntent == null) && alarmModel.onOff) {
            //앱에서는 off, shared에서는 on 경우 -> shared 데이터를 수정
            alarmModel.onOff = false
        } else if ((pendingIntent != null) and alarmModel.onOff.not()) {
            //앱에서는 on, shared에서는 off 경우 -> 알람 취소
            pendingIntent.cancel()
        }

        return alarmModel
    }

    private fun renderView(model: AlarmDisplayModel) {
        findViewById<TextView>(R.id.ampmTextView).apply {
            text = model.ampmText
        }

        findViewById<TextView>(R.id.timeTextView).apply {
            text = model.timeText
        }

        findViewById<Button>(R.id.onoffButton).apply {
            text = model.onOffText
            tag = model //tag는 임시저장소 같은 느낌
        }
    }

    private fun cancelAlarm() {
        val pendingIntent = PendingIntent.getBroadcast(this, ALARM_REQUEST_CODE, Intent(this, AlarmReceiver::class.java), PendingIntent.FLAG_NO_CREATE)
        pendingIntent?.cancel()
    }

    //static 이라고 생각하면 된다고함
    companion object {
        private const val SHARED_PREFERENCES_NAME = "time"
        private const val ALARM_KEY = "alarm"
        private const val ONOFF_KEY = "onOff"
        private const val ALARM_REQUEST_CODE = 1000
    }
}