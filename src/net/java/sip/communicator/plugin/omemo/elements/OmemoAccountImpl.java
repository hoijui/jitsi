package net.java.sip.communicator.plugin.omemo.elements;

import net.java.sip.communicator.service.protocol.jabber.*;
import net.jomemo.elements.*;

/**
 *
 * @author hoijui
 */
public class OmemoAccountImpl implements OmemoAccount
{
    private final JabberAccountID internalAccount;

    public OmemoAccountImpl(final JabberAccountID internalAccount)
    {
        this.internalAccount = internalAccount;
    }

    @Override
    public boolean setKey(String keyId, String value)
    {
        internalAccount.
    }

    @Override
    public String getKey(String keyId)
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OmemoJid getJid()
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OmemoRoster getRoster()
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getPrivateKeyAlias()
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OmemoXmppConnection getXmppConnection()
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
