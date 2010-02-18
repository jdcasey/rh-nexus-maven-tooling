package com.redhat.rcm.nexus.capture.model;

import java.util.Date;

import com.google.gson.annotations.SerializedName;
import com.redhat.rcm.nexus.capture.serialize.SerializationConstants;
import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias( SerializationConstants.SESSION_REF_ROOT )
public class CaptureSessionRef
    implements Comparable<CaptureSessionRef>
{

    private final String user;

    @SerializedName( SerializationConstants.BUILD_TAG_FIELD )
    @XStreamAlias( SerializationConstants.BUILD_TAG_FIELD )
    private final String buildTag;

    @SerializedName( SerializationConstants.CAPTURE_SOURCE_FIELD )
    @XStreamAlias( SerializationConstants.CAPTURE_SOURCE_FIELD )
    private final String captureSource;

    @SerializedName( SerializationConstants.DATE_FIELD )
    @XStreamAlias( SerializationConstants.DATE_FIELD )
    private final Date date;

    // used for gson deserialization
    @SuppressWarnings( "unused" )
    private CaptureSessionRef()
    {
        this.user = null;
        this.buildTag = null;
        this.captureSource = null;
        this.date = null;
    }

    public CaptureSessionRef( final String user, final String buildTag, final String captureSource, final Date date )
    {
        this.user = user;
        this.buildTag = buildTag;
        this.captureSource = captureSource;
        this.date = date;
    }

    public String getUser()
    {
        return user;
    }

    public String getBuildTag()
    {
        return buildTag;
    }

    public String getCaptureSource()
    {
        return captureSource;
    }

    public Date getDate()
    {
        return date;
    }

    public int compareTo( final CaptureSessionRef ref )
    {
        return date.compareTo( ref.date );
    }

    public String key()
    {
        return CaptureSession.key( buildTag, captureSource, user );
    }

}
