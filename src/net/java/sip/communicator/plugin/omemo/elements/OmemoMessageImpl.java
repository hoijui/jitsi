package net.java.sip.communicator.plugin.omemo.elements;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.jabber.*;
import net.jomemo.elements.*;

/**
 *
 * @author hoijui
 */
public class OmemoMessageImpl implements OmemoMessage
{
    private final MessageJabberImpl internal;

    public OmemoMessageImpl(final MessageJabberImpl internal)
    {
        this.internal = internal;
    }

    @Override
    public boolean hasFileOnRemoteHost()
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public FileParams getFileParams()
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getBody()
    {
        return internal.getContent();
    }

    @Override
    public String getUuid()
    {
        return internal.getMessageUID();
    }

    @Override
    public OmemoContact getContact()
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
