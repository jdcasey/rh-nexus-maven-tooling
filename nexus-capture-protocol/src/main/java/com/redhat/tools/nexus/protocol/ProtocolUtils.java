package com.redhat.tools.nexus.protocol;

import static org.codehaus.plexus.util.StringUtils.isNotEmpty;

import org.sonatype.nexus.artifact.Gav;
import org.sonatype.nexus.artifact.IllegalArtifactCoordinateException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ProtocolUtils
{

    private static final TypeToken<TreeMap<Date, File>> DATE_TO_FILE_MAP_TT = new TypeToken<TreeMap<Date, File>>()
    {
    };

    private ProtocolUtils()
    {
    }

    /**
     * Provided here in addition to {@link com.redhat.tools.nexus.request.PathUtils#buildUri(String, String...)} in order to allow
     * decoupling this protocol jar from rh-commons, which brings in restlet and other heavy deps.
     */
    public static String buildUri( final String applicationUrl, final String... parts )
    {
        if ( applicationUrl == null )
        {
            return null;
        }

        final StringBuilder sb = new StringBuilder();
        if ( isNotEmpty( applicationUrl ) )
        {
            if ( applicationUrl.endsWith( "/" ) )
            {
                sb.append( applicationUrl.substring( 0, applicationUrl.length() - 1 ) );
            }
            else
            {
                sb.append( applicationUrl );
            }
        }
        else
        {
            sb.append( '/' );
        }

        for ( final String part : parts )
        {
            if ( isNotEmpty( part ) )
            {
                if ( sb.charAt( sb.length() - 1 ) != '/' && part.charAt( 0 ) != '/' )
                {
                    sb.append( '/' );
                }

                sb.append( part );
            }
        }

        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * Provided here in addition to {@link com.redhat.tools.nexus.request.RequestUtils#formatUrlDate(Date)} in order to allow
     * decoupling this protocol jar from rh-commons, which brings in restlet and other heavy deps.
     */
    public static String formatUrlDate( final Date date )
    {
        return date == null ? null : new SimpleDateFormat( ProtocolConstants.FULL_DATE_FORMAT ).format( date );
    }

    public static Gson getGson()
    {
        return new GsonBuilder().setPrettyPrinting()
                                .registerTypeAdapter( Gav.class, new GavCreator() )
                                .registerTypeAdapter(
                                                      DATE_TO_FILE_MAP_TT.getType(),
                                                      new DateToFileMapTypeAdapter( ProtocolConstants.FULL_DATE_FORMAT,
                                                                                    "session" ) )
                                .create();
    }

    public static XStream getXStreamForREST()
    {
        final XStream xs = createXStream();

        xs.registerLocalConverter( CaptureTargetResource.class, "processedRepositories",
                                   new StringListConverter( "repository" ) );

        // REST resource DTOs
        xs.processAnnotations( CaptureSessionResource.class );
        xs.processAnnotations( CaptureTargetResource.class );
        xs.processAnnotations( CaptureSessionRefResource.class );

        return xs;
    }

    private static XStream createXStream()
    {
        final XStream xs = new XStream();

        xs.setMode( XStream.NO_REFERENCES );
        xs.registerConverter( new CustomFormatDateConverter( ProtocolConstants.FULL_DATE_FORMAT ) );

        return xs;
    }

    public static final class GavCreator
        implements InstanceCreator<Gav>
    {
        public Gav createInstance( final Type type )
        {
            try
            {
                return new Gav( null, null, null, null, null, null, null, null, false, false, null, false, null );
            }
            catch ( final IllegalArtifactCoordinateException e )
            {
                throw new IllegalStateException( String.format( "Failed to create deserialization target: GAV."
                    + "\nReason: %1$s", e.getMessage() ), e );
            }
        }

    }

    public static final class DateToFileMapTypeAdapter
        implements JsonSerializer<TreeMap<Date, File>>, JsonDeserializer<TreeMap<Date, File>>,
        InstanceCreator<TreeMap<Date, File>>, Converter
    {

        private static final String DATE_PROP = "date";

        private static final String FILE_PROP = "file";

        private final String format;

        private final String entryName;

        public DateToFileMapTypeAdapter( final String format, final String entryName )
        {
            this.format = format;
            this.entryName = entryName;
        }

        public JsonElement serialize( final TreeMap<Date, File> src, final Type typeOfSrc,
                                      final JsonSerializationContext context )
        {
            final JsonArray array = new JsonArray();
            if ( src != null )
            {
                for ( final Map.Entry<Date, File> entry : src.entrySet() )
                {
                    final JsonObject obj = new JsonObject();
                    obj.addProperty( DATE_PROP, new SimpleDateFormat( format ).format( entry.getKey() ) );

                    String path;
                    try
                    {
                        path = entry.getValue().getCanonicalPath();
                    }
                    catch ( final IOException e )
                    {
                        path = entry.getValue().getAbsolutePath();
                    }

                    obj.addProperty( FILE_PROP, path );

                    array.add( obj );
                }
            }

            return array;
        }

        public TreeMap<Date, File> deserialize( final JsonElement json, final Type typeOfT,
                                                final JsonDeserializationContext context )
            throws JsonParseException
        {
            final TreeMap<Date, File> result = new TreeMap<Date, File>();

            final JsonArray array = (JsonArray) json;
            for ( final JsonElement el : array )
            {
                final JsonObject obj = (JsonObject) el;
                final String value = obj.get( DATE_PROP ).getAsString();

                Date d;
                try
                {
                    d = new SimpleDateFormat( format ).parse( value );
                }
                catch ( final ParseException e )
                {
                    throw new IllegalArgumentException( String.format( "Cannot parse date: '%s' using format: '%s'",
                                                                       value, format ), e );
                }

                final File f = new File( obj.get( FILE_PROP ).getAsString() );

                result.put( d, f );
            }

            return result;
        }

        public TreeMap<Date, File> createInstance( final Type type )
        {
            return new TreeMap<Date, File>();
        }

        @SuppressWarnings( "unchecked" )
        public void marshal( final Object source, final HierarchicalStreamWriter writer,
                             final MarshallingContext context )
        {
            final TreeMap<Date, File> map = (TreeMap<Date, File>) source;
            for ( final Map.Entry<Date, File> entry : map.entrySet() )
            {
                writer.startNode( entryName );

                writer.startNode( DATE_PROP );
                writer.setValue( new SimpleDateFormat( format ).format( entry.getKey() ) );
                writer.endNode();

                String path;
                try
                {
                    path = entry.getValue().getCanonicalPath();
                }
                catch ( final IOException e )
                {
                    path = entry.getValue().getAbsolutePath();
                }

                writer.startNode( FILE_PROP );
                writer.setValue( path );
                writer.endNode();

                writer.endNode();
            }
        }

        public Object unmarshal( final HierarchicalStreamReader reader, final UnmarshallingContext context )
        {
            final TreeMap<Date, File> result = new TreeMap<Date, File>();

            reader.moveDown();
            while ( reader.hasMoreChildren() )
            {
                reader.moveDown();
                Date d = null;
                File f = null;
                while ( reader.hasMoreChildren() )
                {
                    if ( reader.getNodeName().equals( DATE_PROP ) )
                    {
                        final String value = reader.getValue();
                        try
                        {
                            d = new SimpleDateFormat( format ).parse( value );
                        }
                        catch ( final ParseException e )
                        {
                            throw new IllegalArgumentException(
                                                                String.format(
                                                                               "Cannot parse date: '%s' using format: '%s'",
                                                                               value, format ), e );
                        }
                    }
                    else if ( reader.getNodeName().equals( FILE_PROP ) )
                    {
                        f = new File( reader.getValue() );
                    }
                    else
                    {
                        throw new IllegalArgumentException( String.format( "Invalid element: '%s'",
                                                                           reader.getNodeName() ) );
                    }

                    if ( d != null && f != null )
                    {
                        break;
                    }
                }

                if ( d != null && f != null )
                {
                    result.put( d, f );
                }

                reader.moveUp();
            }
            reader.moveUp();

            return result;
        }

        @SuppressWarnings( "unchecked" )
        public boolean canConvert( final Class type )
        {
            return TreeMap.class.isAssignableFrom( type );
        }

    }

    public static final class CustomFormatDateConverter
        implements Converter
    {

        private final String format;

        public CustomFormatDateConverter( final String format )
        {
            this.format = format;
        }

        public void marshal( final Object source, final HierarchicalStreamWriter writer,
                             final MarshallingContext context )
        {
            writer.setValue( new SimpleDateFormat( format ).format( (Date) source ) );
        }

        public Object unmarshal( final HierarchicalStreamReader reader, final UnmarshallingContext context )
        {
            final String value = reader.getValue();
            try
            {
                return new SimpleDateFormat( format ).parseObject( value );
            }
            catch ( final ParseException e )
            {
                throw new IllegalArgumentException( String.format( "Cannot parse date: '%s' using format: '%s'", value,
                                                                   format ), e );
            }
        }

        @SuppressWarnings( "unchecked" )
        public boolean canConvert( final Class type )
        {
            return Date.class.isAssignableFrom( type );
        }

    }

    public static final class StringListConverter
        implements Converter
    {
        private final String itemName;

        public StringListConverter( final String itemName )
        {
            this.itemName = itemName;
        }

        @SuppressWarnings( "unchecked" )
        public void marshal( final Object source, final HierarchicalStreamWriter writer,
                             final MarshallingContext context )
        {
            final List<String> values = (List<String>) source;

            for ( final String value : values )
            {
                writer.startNode( itemName );
                writer.setValue( value );
                writer.endNode();
            }
        }

        public Object unmarshal( final HierarchicalStreamReader reader, final UnmarshallingContext context )
        {
            final List<String> result = new ArrayList<String>();

            reader.moveDown();
            while ( reader.hasMoreChildren() )
            {
                result.add( reader.getValue() );
            }
            reader.moveUp();

            return result;
        }

        @SuppressWarnings( "unchecked" )
        public boolean canConvert( final Class type )
        {
            return List.class.isAssignableFrom( type );
        }
    }
}
