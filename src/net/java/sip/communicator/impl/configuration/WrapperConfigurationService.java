/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.java.sip.communicator.impl.configuration;

import java.beans.*;
import java.io.*;
import java.util.*;
import org.jitsi.service.configuration.*;
import org.jitsi.util.*;

/**
 *
 * @author hoijui
 */

public class WrapperConfigurationService implements ConfigurationService
{
    /**
     * The <tt>Logger</tt> used by the <tt>WrapperConfigurationService</tt>
     * class for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(WrapperConfigurationService.class);

    private final ConfigurationService innerConfigurationService;

    public WrapperConfigurationService(final ConfigurationService innerConfigurationService)
    {
        logger.error("XXX WrapperConfigurationService ctor");
        this.innerConfigurationService = innerConfigurationService;
    }

    public void setProperty(String string, Object o)
    {
        innerConfigurationService.setProperty(string, o);
    }

    public void setProperty(String string, Object o, boolean bln)
    {
        innerConfigurationService.setProperty(string, o, bln);
    }

    public void setProperties(Map<String, Object> map)
    {
        innerConfigurationService.setProperties(map);
    }

    public Object getProperty(String string)
    {
logger.error("XXX getProperty(\"" + string + "\")");
        return innerConfigurationService.getProperty(string);
    }

    public void removeProperty(String string)
    {
        innerConfigurationService.removeProperty(string);
    }

    public List<String> getAllPropertyNames()
    {
        return innerConfigurationService.getAllPropertyNames();
    }

    public List<String> getPropertyNamesByPrefix(String string, boolean bln)
    {
        return innerConfigurationService.getPropertyNamesByPrefix(string, bln);
    }

    public List<String> getPropertyNamesBySuffix(String string)
    {
        return innerConfigurationService.getPropertyNamesBySuffix(string);
    }

    public String getString(String string)
    {
logger.error("XXX getString(\"" + string + "\")");
        return innerConfigurationService.getString(string);
    }

    public String getString(String string, String string1)
    {
logger.error("XXX getString(\"" + string + "\")");
        return innerConfigurationService.getString(string, string1);
    }

    public boolean getBoolean(String string, boolean bln)
    {
logger.error("XXX getBoolean(\"" + string + "\")");
        return innerConfigurationService.getBoolean(string, bln);
    }

    public int getInt(String string, int i)
    {
logger.error("XXX getInt(\"" + string + "\")");
        return innerConfigurationService.getInt(string, i);
    }

    public long getLong(String string, long l)
    {
logger.error("XXX getLong(\"" + string + "\")");
        return innerConfigurationService.getLong(string, l);
    }

    public void addPropertyChangeListener(PropertyChangeListener pl)
    {
        innerConfigurationService.addPropertyChangeListener(pl);
    }

    public void removePropertyChangeListener(PropertyChangeListener pl)
    {
        innerConfigurationService.removePropertyChangeListener(pl);
    }

    public void addPropertyChangeListener(String string, PropertyChangeListener pl)
    {
        innerConfigurationService.addPropertyChangeListener(string, pl);
    }

    public void removePropertyChangeListener(String string, PropertyChangeListener pl)
    {
        innerConfigurationService.removePropertyChangeListener(string, pl);
    }

    public void addVetoableChangeListener(ConfigVetoableChangeListener cl)
    {
        innerConfigurationService.addVetoableChangeListener(cl);
    }

    public void removeVetoableChangeListener(ConfigVetoableChangeListener cl)
    {
        innerConfigurationService.removeVetoableChangeListener(cl);
    }

    public void addVetoableChangeListener(String string, ConfigVetoableChangeListener cl)
    {
        innerConfigurationService.addVetoableChangeListener(string, cl);
    }

    public void removeVetoableChangeListener(String string, ConfigVetoableChangeListener cl)
    {
        innerConfigurationService.removeVetoableChangeListener(string, cl);
    }

    public void storeConfiguration()
            throws IOException
    {
        innerConfigurationService.storeConfiguration();
    }

    public void reloadConfiguration()
            throws IOException
    {
        innerConfigurationService.reloadConfiguration();
    }

    public void purgeStoredConfiguration()
    {
        innerConfigurationService.purgeStoredConfiguration();
    }

    public String getScHomeDirName()
    {
        return innerConfigurationService.getScHomeDirName();
    }

    public String getScHomeDirLocation()
    {
        return innerConfigurationService.getScHomeDirLocation();
    }

    public String getConfigurationFilename()
    {
        return innerConfigurationService.getConfigurationFilename();
    }
}
