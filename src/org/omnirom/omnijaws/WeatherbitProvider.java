package org.omnirom.omnijaws;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.omnirom.omnijaws.WeatherInfo.DayForecast;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WeatherbitProvider extends AbstractWeatherProvider {

    private static final String TAG = WeatherbitProvider.class.getSimpleName();

    private static final int FORECAST_DAYS = 5;
    private static final String API_KEY = "5a10320533994da2af61cf7f9d3e2058";
    private static final String FORECAST_URL =
            "https://api.weatherbit.io/v2.0/forecast/daily?%s%%20&days=%d&lang=%s&key=%s";
    private static final String CURRENT_URL =
            "https://api.weatherbit.io/v2.0/current?%s&lang=%s&key=%s";
    private static final String COORDINATES = "lat=%f&lon=%f";
    private static final Map<String, String> LANGUAGE_CODE_MAPPING= new HashMap<>();

    static {
        LANGUAGE_CODE_MAPPING.put("bg-", "bg");
        LANGUAGE_CODE_MAPPING.put("de-", "de");
        LANGUAGE_CODE_MAPPING.put("fi-", "fi");
        LANGUAGE_CODE_MAPPING.put("fr-", "fr");
        LANGUAGE_CODE_MAPPING.put("it-", "it");
        LANGUAGE_CODE_MAPPING.put("nl-", "nl");
        LANGUAGE_CODE_MAPPING.put("pl-", "pl");
        LANGUAGE_CODE_MAPPING.put("pt-", "pt");
        LANGUAGE_CODE_MAPPING.put("ro-", "ro");
        LANGUAGE_CODE_MAPPING.put("ru-", "ru");
        LANGUAGE_CODE_MAPPING.put("se-", "se");
        LANGUAGE_CODE_MAPPING.put("tr-", "tr");
        LANGUAGE_CODE_MAPPING.put("uk-", "ua");
        LANGUAGE_CODE_MAPPING.put("zh-CN", "zh");
        LANGUAGE_CODE_MAPPING.put("zh-TW", "zh-tw");
    }

    public WeatherbitProvider(Context context) {
        super(context);
    }

    @Override
    public WeatherInfo getCustomWeather(String id, boolean metric) {
        return null;
    }

    @Override
    public WeatherInfo getLocationWeather(Location location, boolean metric) {
        String coordinates = String.format(Locale.US, COORDINATES, location.getLatitude(), location.getLongitude());
        return handleWeatherRequest(coordinates, metric);
    }

    @Override
    public List<WeatherInfo.WeatherLocation> getLocations(String input) {
        return null;
    }

    @Override
    public Map<String, String> getLanguageMapping() {
        return LANGUAGE_CODE_MAPPING;
    }

    @Override
    public boolean shouldRetry() {
        return false;
    }

    private WeatherInfo handleWeatherRequest(String coordinates, boolean metric) {
        String forecastURL = String.format(Locale.US, FORECAST_URL, coordinates, FORECAST_DAYS, getLanguageCode(), API_KEY);
        String forecastResponse = retrieve(forecastURL);
        Log.d(TAG, "Server response: " + forecastResponse);
        ArrayList<DayForecast> forecasts = new ArrayList<>();
        try {
            forecasts = parseForecast(new JSONObject(forecastResponse).getJSONArray("data"), metric);
        } catch (JSONException e) {
            Log.e(TAG, "Malformed json: forecast parsing failed!");
            e.printStackTrace();
        }

        String currentCondition = String.format(Locale.US, CURRENT_URL, coordinates, getLanguageCode(), API_KEY);
        String currentConditionResponse = retrieve(currentCondition);
        Log.d(TAG, "Server response: " + currentConditionResponse);

        WeatherInfo weatherInfo = null;
        try {
            JSONObject data = new JSONObject(currentConditionResponse);
            JSONObject conditions = data.getJSONArray("data").getJSONObject(0);
            JSONObject weather = conditions.getJSONObject("weather");

            float windSpeed = (float) conditions.getDouble("wind_spd");

            weatherInfo = new WeatherInfo(mContext,
                    /* id */ conditions.getString("ts"),
                    /* city name*/ conditions.getString("city_name"),
                    /* condition */ weather.getString("description"),
                    /* conditionCode */ mapConditionIconToCode(
                    weather.getString("icon"), weather.getInt("code")),
                    /* temperature */ (float) conditions.getDouble("temp"),
                    /* humidity */ (float) conditions.getDouble("rh"),
                    /* wind */ metric ? convertTokmh(windSpeed) : windSpeed,
                    /* windDir */ conditions.getInt("wind_dir"),
                    metric,
                    forecasts,
                    System.currentTimeMillis());
            Log.d(TAG, "Weather updated: " + weatherInfo);
        } catch (JSONException e) {
            Log.e(TAG, "Malformed json: current weather parsing failed!");
            e.printStackTrace();
        }
        return  weatherInfo;
    }

    private float convertTokmh(float windSpeed) {
        return windSpeed * 3.6f;
    }

    private ArrayList<DayForecast> parseForecast(JSONArray forecasts, boolean metric) throws JSONException {
        ArrayList<DayForecast> result = new ArrayList<DayForecast>();
        int count = forecasts.length();

        if (count == 0) {
            throw new JSONException("Empty forecasts array");
        }
        for (int i = 0; i < count; i++) {
            DayForecast item = null;
            try {
                JSONObject forecast = forecasts.getJSONObject(i);
                JSONObject weather = forecast.getJSONObject("weather");
                item = new DayForecast(
                        /* low */ (float) forecast.getDouble("min_temp"),
                        /* high */ (float) forecast.getDouble("max_temp"),
                        /* condition */ weather.getString("description"),
                        /* conditionCode */ mapConditionIconToCode(
                        weather.getString("icon"), weather.getInt("code")),
                        forecast.getString("valid_date"),
                        metric);
            } catch (JSONException e) {
                Log.w(TAG, "Invalid forecast for day " + i + " creating dummy", e);
                item = new DayForecast(
                        /* low */ 0,
                        /* high */ 0,
                        /* condition */ "",
                        /* conditionCode */ -1,
                        "1970-04-27",
                        metric);
            }
            result.add(item);
        }
        return result;
    }

}
