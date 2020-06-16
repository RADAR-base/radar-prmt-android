package org.radarcns.android.auth;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
@Deprecated
public class AppSource implements Parcelable, Serializable {
    private final long sourceTypeId;
    private final String sourceTypeProducer;
    private final String sourceTypeModel;
    private final String sourceTypeCatalogVersion;
    private final boolean dynamicRegistration;
    private String sourceId;
    private String sourceName;
    private String expectedSourceName;
    private Map<String, String> attributes;

    public static final Creator<AppSource> CREATOR = new Creator<AppSource>() {
        @Override
        public AppSource createFromParcel(Parcel parcel) {
            AppSource source = new AppSource(parcel.readLong(), parcel.readString(), parcel.readString(),
                    parcel.readString(), parcel.readByte() == 1);
            source.setSourceId(parcel.readString());
            source.setSourceName(parcel.readString());
            source.setExpectedSourceName(parcel.readString());
            int len = parcel.readInt();
            Map<String, String> attr = new HashMap<>(len * 4 / 3 + 1);
            for (int i = 0; i < len; i++) {
                attr.put(parcel.readString(), parcel.readString());
            }
            source.setAttributes(attr);
            return source;
        }

        @Override
        public AppSource[] newArray(int i) {
            return new AppSource[i];
        }
    };

    public AppSource(long deviceTypeId, String deviceProducer, String deviceModel, String catalogVersion,
            boolean dynamicRegistration) {
        this.sourceTypeId = deviceTypeId;
        this.sourceTypeProducer = deviceProducer;
        this.sourceTypeModel = deviceModel;
        this.sourceTypeCatalogVersion = catalogVersion;
        this.dynamicRegistration = dynamicRegistration;
        this.attributes = new HashMap<>();
    }

    public String getSourceTypeProducer() {
        return sourceTypeProducer;
    }

    public String getSourceTypeModel() {
        return sourceTypeModel;
    }

    public String getSourceTypeCatalogVersion() {
        return sourceTypeCatalogVersion;
    }

    public boolean hasDynamicRegistration() {
        return dynamicRegistration;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getExpectedSourceName() {
        return expectedSourceName;
    }

    public void setExpectedSourceName(String expectedSourceName) {
        this.expectedSourceName = expectedSourceName;
    }

    @NonNull
    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public void setAttributes(Map<? extends String, ? extends String> attributes) {
        this.attributes.clear();
        if (attributes != null) {
            this.attributes.putAll(attributes);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AppSource appSource = (AppSource) o;
        return sourceTypeId == appSource.sourceTypeId
                && dynamicRegistration == appSource.dynamicRegistration
                && Objects.equals(sourceTypeProducer, appSource.sourceTypeProducer)
                && Objects.equals(sourceTypeModel, appSource.sourceTypeModel)
                && Objects.equals(sourceTypeCatalogVersion, appSource.sourceTypeCatalogVersion)
                && Objects.equals(sourceId, appSource.sourceId)
                && Objects.equals(sourceName, appSource.sourceName)
                && Objects.equals(expectedSourceName, appSource.expectedSourceName)
                && Objects.equals(attributes, appSource.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceTypeId, sourceTypeProducer, sourceTypeModel, sourceTypeCatalogVersion,
                dynamicRegistration, sourceId, sourceName, expectedSourceName, attributes);
    }

    @Override
    public String toString() {
        return "AppSource{"
                + "sourceTypeId='" + sourceTypeId + '\''
                + ", sourceTypeProducer='" + sourceTypeProducer + '\''
                + ", sourceTypeModel='" + sourceTypeModel + '\''
                + ", sourceTypeCatalogVersion='" + sourceTypeCatalogVersion + '\''
                + ", dynamicRegistration=" + dynamicRegistration
                + ", sourceId='" + sourceId + '\''
                + ", sourceName='" + sourceName + '\''
                + ", expectedSourceName='" + expectedSourceName + '\''
                + ", attributes=" + attributes + '\''
                + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(sourceTypeId);
        parcel.writeString(sourceTypeProducer);
        parcel.writeString(sourceTypeModel);
        parcel.writeString(sourceTypeCatalogVersion);
        parcel.writeByte(dynamicRegistration ? (byte)1 : (byte)0);
        parcel.writeString(sourceId);
        parcel.writeString(sourceName);
        parcel.writeString(expectedSourceName);
        parcel.writeInt(attributes.size());
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            parcel.writeString(entry.getKey());
            parcel.writeString(entry.getValue());
        }
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public long getSourceTypeId() {
        return sourceTypeId;
    }
}
