package net.java.sip.communicator.plugin.omemo.elements;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.jabber.*;
import net.jomemo.elements.*;

/**
 *
 * @author hoijui
 */
public class OmemoIqParserImpl implements OmemoIqParser
{
    private final MessageJabberImpl internal;

    public OmemoIqParserImpl(final MessageJabberImpl internal)
    {
        this.internal = internal;
    }

}
