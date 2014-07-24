package ar.com.guzik.sunshine.app.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

import org.apache.http.impl.client.RedirectLocations;

import java.sql.SQLException;

/**
 * Created by dguzik on 7/23/14.
 */
public class WeatherProvider extends ContentProvider {

    private static final int WEATHER = 100;
    private static final int WEATHER_WITH_LOCATION = 101;
    private static final int WEATHER_WITH_LOCATION_AND_DATE = 102;
    private static final int LOCATION = 300;
    private static final int LOCATION_ID = 301;

    private static UriMatcher matcher = buildUriMatcher();
    private WeatherDBHelper mOpenHelper;

    private static final SQLiteQueryBuilder sWeatherByLocationSettingQueryBuilder;
    static {
        sWeatherByLocationSettingQueryBuilder = new SQLiteQueryBuilder();
        sWeatherByLocationSettingQueryBuilder.setTables(
                WeatherContract.WeatherEntry.TABLE_NAME + " INNER JOIN " +
                        LocationContract.LocationEntry.TABLE_NAME +
                        " ON " + WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry.COLUMN_LOC_KEY +
                        " = " + LocationContract.LocationEntry.TABLE_NAME + "." + LocationContract.LocationEntry._ID);

    }

    private static final String sLocationSettingSelection =
            LocationContract.LocationEntry.TABLE_NAME +
                    "." + LocationContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ? ";
    private static final String sLocationSettingWithStartDateSelection =
            LocationContract.LocationEntry.TABLE_NAME +
                    "." + LocationContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ? AND " +
                    WeatherContract.WeatherEntry.COLUMN_DATETEXT + " >= ? ";
    private static final String sLocationSettingWithDateSelection =
            LocationContract.LocationEntry.TABLE_NAME +
                    "." + LocationContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ? AND " +
                    WeatherContract.WeatherEntry.COLUMN_DATETEXT + " = ? ";
    private final String LOG_TAG = WeatherProvider.class.getSimpleName();

    private Cursor getWeatherByLocationSetting( Uri uri, String[] projection, String sortOrder) {
        String locationSetting = WeatherContract.WeatherEntry.getLocationSettingFromUri(uri);
        String startDate = WeatherContract.WeatherEntry.getStartDateFromUri(uri);

        String[] selectionArgs;
        String selection;
        if (startDate == null) {
            selection = sLocationSettingSelection;
            selectionArgs = new String[] {locationSetting};
        } else {
            selectionArgs = new String[] {locationSetting, startDate};
            selection = sLocationSettingWithStartDateSelection;
        }
        return sWeatherByLocationSettingQueryBuilder.query(mOpenHelper.getWritableDatabase(),
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
                );
    }
    @Override
    public boolean onCreate() {
        mOpenHelper = new WeatherDBHelper((getContext()));
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // Here's the switch statement that, given a URI, will determine what kind of request it is,
        // and query the database accordingly.
        Cursor retCursor;
        Log.w(LOG_TAG, uri.toString());
        switch (matcher.match(uri)) {
            // "weather/*/*"
            case WEATHER_WITH_LOCATION_AND_DATE:
            {
                return getWeatherByLocationSettingWithDate(uri, projection, sortOrder);
            }
            // "weather/*"
            case WEATHER_WITH_LOCATION: {
                return getWeatherByLocationSetting(uri, projection, sortOrder);
            }
            // "weather"
            case WEATHER: {
                retCursor = mOpenHelper.getReadableDatabase().query(
                        WeatherContract.WeatherEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            }
            // "location/*"
            case LOCATION_ID: {
                long id = ContentUris.parseId(uri);
                retCursor = mOpenHelper.getReadableDatabase().query(
                        LocationContract.LocationEntry.TABLE_NAME,
                        projection,
                        WeatherContract.WeatherEntry._ID + " = '" + id + "' ",
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            }
            // "location"
            case LOCATION: {
                retCursor = mOpenHelper.getReadableDatabase().query(
                        LocationContract.LocationEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            }

            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        retCursor.setNotificationUri(getContext().getContentResolver(), uri);
        return retCursor;

    }

    @Override
    public String getType(Uri uri) {
        final int match = matcher.match(uri);
        switch (match) {
            case WEATHER_WITH_LOCATION:
                return WeatherContract.WeatherEntry.CONTENT_ITEM_TYPE;
            case WEATHER_WITH_LOCATION_AND_DATE:
                return WeatherContract.WeatherEntry.CONTENT_ITEM_TYPE;
            case WEATHER:
                return WeatherContract.WeatherEntry.CONTENT_TYPE;
            case LOCATION:
                return LocationContract.LocationEntry.CONTENT_TYPE;
            case LOCATION_ID:
                return LocationContract.LocationEntry.CONTENT_ITEM_TYPE;

            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Uri retUri;
        final int match = matcher.match(uri);
        switch (match) {
            case WEATHER:
                // Fantastic.  Now that we have a location, add some weather!
                long weatherRowId = db.insert(WeatherContract.WeatherEntry.TABLE_NAME, null, values);
                if (weatherRowId > 0)
                    retUri =  WeatherContract.WeatherEntry.buildWeatherUri(weatherRowId);
                else
                    throw new android.database.SQLException("Failed to insert row into " + uri);
                break;
            case LOCATION:

                long locationRowId = db.insert(LocationContract.LocationEntry.TABLE_NAME, null, values);
                if (locationRowId > 0)
                    retUri =  LocationContract.LocationEntry.buildLocationUri(locationRowId);
                 else
                    throw new android.database.SQLException("Failed to insert row into " + uri);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return retUri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
    private static UriMatcher buildUriMatcher() {
        UriMatcher u = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = WeatherContract.CONTENT_AUTHORITY;
        u.addURI(authority, WeatherContract.PATH_WEATHER, WEATHER);
        u.addURI(authority, WeatherContract.PATH_WEATHER + "/*", WEATHER_WITH_LOCATION);
        u.addURI(authority, WeatherContract.PATH_WEATHER + "/*/*", WEATHER_WITH_LOCATION_AND_DATE);
        u.addURI(authority, LocationContract.PATH_LOCATION, LOCATION);
        u.addURI(authority, LocationContract.PATH_LOCATION + "/#", LOCATION_ID);
        return u;
    }

    private Cursor getWeatherByLocationSettingWithDate( Uri uri, String[] projection, String sortOrder) {
        String locationSetting = WeatherContract.WeatherEntry.getLocationSettingFromUri(uri);
        String day = WeatherContract.WeatherEntry.getDateFromUri(uri);

        return sWeatherByLocationSettingQueryBuilder.query(mOpenHelper.getWritableDatabase(),
                projection,
                sLocationSettingWithDateSelection,
                new String[] {locationSetting, day},
                null,
                null,
                sortOrder
        );
    }

}
