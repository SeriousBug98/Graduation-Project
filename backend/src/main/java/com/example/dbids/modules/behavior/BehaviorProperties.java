package com.example.dbids.modules.behavior;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "behavior")
public class BehaviorProperties {

    /** 윈도우 크기(초). 기본 60 */
    private int windowSeconds = 60;

    /** 점수 임계치 (상수 임계 모드; SDS 범위 내 튜닝) */
    private double thresholdMedium = 3.0;
    private double thresholdHigh   = 6.0;

    /** 지표 가중치 */
    private double wQpm        = 1.0;
    private double wWriteRatio = 1.0;
    private double wDdlPerMin  = 1.0;
    private double wErrorBurst = 0.5;

    // getters / setters
    public int getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }

    public double getThresholdMedium() { return thresholdMedium; }
    public void setThresholdMedium(double thresholdMedium) { this.thresholdMedium = thresholdMedium; }

    public double getThresholdHigh() { return thresholdHigh; }
    public void setThresholdHigh(double thresholdHigh) { this.thresholdHigh = thresholdHigh; }

    public double getWQpm() { return wQpm; }
    public void setWQpm(double wQpm) { this.wQpm = wQpm; }

    public double getWWriteRatio() { return wWriteRatio; }
    public void setWWriteRatio(double wWriteRatio) { this.wWriteRatio = wWriteRatio; }

    public double getWDdlPerMin() { return wDdlPerMin; }
    public void setWDdlPerMin(double wDdlPerMin) { this.wDdlPerMin = wDdlPerMin; }

    public double getWErrorBurst() { return wErrorBurst; }
    public void setWErrorBurst(double wErrorBurst) { this.wErrorBurst = wErrorBurst; }


}
