package com.redhat.rcm.nexus.capture;

import static com.redhat.rcm.nexus.capture.model.ModelSerializationUtils.getGson;
import static com.redhat.rcm.nexus.capture.request.RequestUtils.mediaTypeOf;
import static com.redhat.rcm.nexus.capture.request.RequestUtils.parseUrlDate;
import static com.redhat.rcm.nexus.capture.request.RequestUtils.query;
import static com.redhat.rcm.nexus.util.ProtocolUtils.getXStreamForREST;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.rest.AbstractNexusPlexusResource;
import org.sonatype.plexus.rest.resource.PathProtectionDescriptor;
import org.sonatype.plexus.rest.resource.PlexusResource;

import com.redhat.rcm.nexus.capture.model.CaptureSession;
import com.redhat.rcm.nexus.capture.model.CaptureSessionRef;
import com.redhat.rcm.nexus.capture.store.CaptureStore;
import com.redhat.rcm.nexus.capture.store.CaptureStoreException;
import com.redhat.rcm.nexus.capture.template.TemplateConstants;
import com.redhat.rcm.nexus.capture.template.TemplateException;
import com.redhat.rcm.nexus.capture.template.TemplateFormatter;
import com.redhat.rcm.nexus.protocol.ProtocolConstants;

@Named( "captureLog" )
public class CaptureLogResource
    extends AbstractNexusPlexusResource
    implements PlexusResource
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    @Named( "json" )
    private CaptureStore captureStore;

    @Inject
    @Named( "velocity" )
    private TemplateFormatter templateFormatter;

    @Override
    public Object getPayloadInstance()
    {
        // if you allow PUT or POST you would need to return your object.
        return null;
    }

    @Override
    public PathProtectionDescriptor getResourceProtection()
    {
        return new PathProtectionDescriptor( ProtocolConstants.LOG_RESOURCE_FRAGMENT + "/*/*/*",
                                             String.format( "authcBasic,perms[%s]",
                                                            CaptureResourceConstants.PRIV_LOG_ACCESS ) );
    }

    @Override
    public String getResourceUri()
    {
        return ProtocolConstants.LOG_RESOURCE_FRAGMENT + "/{" + CaptureResourceConstants.ATTR_USER + "}/{"
                        + CaptureResourceConstants.ATTR_BUILD_TAG_REPO_ID + "}/{" + CaptureResourceConstants.ATTR_DATE
                        + "}";
    }

    @Override
    public Object get( final Context context, final Request request, final Response response, final Variant variant )
        throws ResourceException
    {
        final String user = request.getAttributes().get( CaptureResourceConstants.ATTR_USER ).toString();

        final String buildTag =
            request.getAttributes().get( CaptureResourceConstants.ATTR_BUILD_TAG_REPO_ID ).toString();

        final String dateValue = request.getAttributes().get( CaptureResourceConstants.ATTR_DATE ).toString();

        Object data = null;
        try
        {
            final Date date = parseUrlDate( dateValue );

            final CaptureSessionRef ref = new CaptureSessionRef( user, buildTag, date );

            final CaptureSession session = captureStore.readLog( ref );
            if ( session != null )
            {
                data = session.asResource( request.getRootRef().toString(), getRepositoryRegistry() );
            }
            else
            {
                throw new ResourceException( Status.CLIENT_ERROR_NOT_FOUND );
            }
        }
        catch ( final CaptureStoreException e )
        {
            final String message =
                String.format( "Failed to retrieve capture log. Error: %s\nMessage: %s", e.getClass().getName(),
                               e.getMessage() );

            logger.error( message, e );
            e.printStackTrace();

            throw new ResourceException( Status.SERVER_ERROR_INTERNAL, message );
        }
        catch ( final ParseException e )
        {
            final String message =
                String.format( "Failed to retrieve capture log. Invalid date format: '%s' Error: %s\nMessage: %s",
                               dateValue, e.getClass().getName(), e.getMessage() );

            logger.error( message, e );
            e.printStackTrace();

            throw new ResourceException( Status.SERVER_ERROR_INTERNAL, message );
        }

        String result = null;

        final MediaType mt = mediaTypeOf( request, variant );
        if ( mt == MediaType.APPLICATION_XML )
        {
            result = getXStreamForREST().toXML( data );
        }
        else if ( mt == MediaType.APPLICATION_JSON )
        {
            result = getGson().toJson( data );
        }
        else if ( mt == MediaType.TEXT_PLAIN )
        {
            final Map<String, Object> templateContext = new HashMap<String, Object>();
            templateContext.put( "data", data );

            final Form query = query( request );
            final String template = query.getFirstValue( CaptureResourceConstants.PARAM_TEMPLATE );

            try
            {
                result = templateFormatter.format( TemplateConstants.LOG_TEMPLATE_BASEPATH, template, templateContext );
            }
            catch ( final TemplateException e )
            {
                throw new ResourceException( Status.SERVER_ERROR_INTERNAL, e.getMessage(), e );
            }
        }
        else
        {
            throw new ResourceException( Status.CLIENT_ERROR_NOT_ACCEPTABLE );
        }

        return new StringRepresentation( result, mt );
    }

    @Override
    public List<Variant> getVariants()
    {
        final List<Variant> variants = new ArrayList<Variant>();

        variants.add( new Variant( MediaType.APPLICATION_XML ) );
        variants.add( new Variant( MediaType.APPLICATION_JSON ) );
        variants.add( new Variant( MediaType.TEXT_PLAIN ) );

        return variants;
    }

}