package com.redhat.rcm.nexus.capture;

import static com.redhat.rcm.nexus.capture.request.RequestUtils.parseUrlDate;
import static com.redhat.rcm.nexus.capture.request.RequestUtils.query;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.codehaus.plexus.component.annotations.Requirement;
import org.restlet.data.Request;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.rest.AbstractNexusPlexusResource;
import org.sonatype.plexus.rest.resource.PlexusResource;

import com.redhat.rcm.nexus.capture.model.CaptureSessionQuery;
import com.redhat.rcm.nexus.capture.model.CaptureSessionRef;
import com.redhat.rcm.nexus.capture.render.CaptureSessionRefResource;
import com.redhat.rcm.nexus.capture.store.CaptureStore;
import com.redhat.rcm.nexus.capture.store.CaptureStoreException;

public abstract class AbstractCaptureLogResource
    extends AbstractNexusPlexusResource
    implements PlexusResource
{

    @Requirement( hint = "json" )
    private CaptureStore captureStore;

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    protected CaptureStore getCaptureStore()
    {
        return captureStore;
    }

    protected void deleteLogs( final String user, final String buildTag, final String captureSource,
                               final Request request )
        throws ResourceException
    {
        final CaptureSessionQuery query =
            new CaptureSessionQuery().setUser( user ).setBuildTag( buildTag ).setCaptureSource( captureSource );

        try
        {
            final String value = query( request ).getFirstValue( CaptureResourceConstants.PARAM_BEFORE );
            final Date before = parseUrlDate( value );

            if ( before != null )
            {
                query.setBefore( before );
            }
        }
        catch ( final ParseException e )
        {
            final String message =
                String.format( "Invalid date format in %s parameter. Error: %s\nMessage: %s",
                               CaptureResourceConstants.PARAM_BEFORE, e.getClass().getName(), e.getMessage() );

            logger.error( message, e );

            e.printStackTrace();

            throw new ResourceException( Status.CLIENT_ERROR_BAD_REQUEST, message );
        }

        try
        {
            getCaptureStore().deleteLogs( query );
        }
        catch ( final CaptureStoreException e )
        {
            final String message =
                String.format( "Failed to expire capture log(s) for query:\n%s\nError: %s\nMessage: %s", query,
                               e.getClass().getName(), e.getMessage() );

            logger.error( message, e );

            e.printStackTrace();

            throw new ResourceException( Status.SERVER_ERROR_INTERNAL, message );
        }
    }

    protected List<CaptureSessionRefResource> queryLogs( final CaptureSessionQuery query, final String appUrl )
        throws CaptureStoreException
    {
        final List<CaptureSessionRef> logs = captureStore.getLogs( query );
        if ( logs != null )
        {
            final List<CaptureSessionRefResource> resources = new ArrayList<CaptureSessionRefResource>( logs.size() );

            for ( final CaptureSessionRef ref : logs )
            {
                resources.add( new CaptureSessionRefResource( ref, appUrl ) );
            }

            return resources;
        }

        return null;
    }
}
