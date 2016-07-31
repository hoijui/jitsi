package net.java.sip.communicator.plugin.omemo.elements;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.jabber.*;
import net.jomemo.elements.*;

/**
 *
 * @author hoijui
 */
public class OmemoIqPacketImpl implements OmemoIqPacket
{
    private final MessageJabberImpl internal;

    public OmemoIqPacketImpl(final MessageJabberImpl internal)
    {
        this.internal = internal;
    }

    @Override
    public TYPE getType()
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OmemoElement findChild(String error)
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
