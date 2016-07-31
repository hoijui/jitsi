/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.plugin.omemo;

import java.awt.event.*;
import java.security.*;

import javax.swing.*;

import net.java.omemo4j.*;
import net.java.sip.communicator.plugin.desktoputil.*;
import net.java.sip.communicator.plugin.omemo.OmemoContactManager.OmemoContact;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;

/**
 * A special {@link JMenu} that holds the menu items for controlling the
 * Off-the-Record functionality for a specific contact.
 *
 * @author George Politis
 * @author Lyubomir Marinov
 * @author Marin Dzhigarov
 */
class OmemoContactMenu
    implements ActionListener,
               ScOmemoEngineListener,
               ScOmemoKeyManagerListener
{
    private final Logger logger = Logger.getLogger(OmemoContactMenu.class);

    private static final String ACTION_COMMAND_AUTHENTICATE_BUDDY =
        "AUTHENTICATE_BUDDY";

    private static final String ACTION_COMMAND_CB_AUTO = "CB_AUTO";

    private static final String ACTION_COMMAND_CB_AUTO_ALL = "CB_AUTO_ALL";

    private static final String ACTION_COMMAND_CB_ENABLE = "CB_ENABLE";

    private static final String ACTION_COMMAND_CB_REQUIRE = "CB_REQUIRE";

    private static final String ACTION_COMMAND_CB_RESET = "CB_RESET";

    private static final String ACTION_COMMAND_END_OTR = "END_OTR";

    private static final String ACTION_COMMAND_REFRESH_OTR = "REFRESH_OTR";

    private static final String ACTION_COMMAND_START_OTR = "START_OTR";

    private final OmemoContact contact;

    /**
     * The indicator which determines whether this <tt>JMenu</tt> is displayed
     * in the Mac OS X screen menu bar and thus should work around the known
     * problem of PopupMenuListener not being invoked.
     */
    private final boolean inMacOSXScreenMenuBar;

    /**
     * We keep this variable so we can determine if the policy has changed
     * or not in {@link OmemoContactMenu#setOtrPolicy(OmemoPolicy)}.
     */
    private OmemoPolicy otrPolicy;

    private ScSessionStatus sessionStatus;

    private final JMenu parentMenu;

    private final SIPCommMenu separateMenu;

    /**
     * The OmemoContactMenu constructor.
     *
     * @param otrContact the OmemoContact this menu refers to.
     * @param inMacOSXScreenMenuBar <tt>true</tt> if the new menu is to be
     * displayed in the Mac OS X screen menu bar; <tt>false</tt>, otherwise
     * @param menu the parent menu
     */
    public OmemoContactMenu(  OmemoContact otrContact,
                            boolean inMacOSXScreenMenuBar,
                            JMenu menu,
                            boolean isSeparateMenu)
    {
        this.contact = otrContact;
        this.inMacOSXScreenMenuBar = inMacOSXScreenMenuBar;
        this.parentMenu = menu;
        String resourceName =
            otrContact.resource != null
                ? "/" + otrContact.resource.getResourceName()
                : "";
        separateMenu
            = isSeparateMenu
                ? new SIPCommMenu(otrContact.contact.getDisplayName()
                                    + resourceName)
                : null;

        /*
         * XXX This OmemoContactMenu instance cannot be added as a listener to
         * scOmemoEngine and scOmemoKeyManager without being removed later on
         * because the latter live forever. Unfortunately, the dispose() method
         * of this instance is never executed. OmemoWeakListener will keep this
         * instance as a listener of scOmemoEngine and scOmemoKeyManager for as long
         * as this instance is necessary. And this instance will be strongly
         * referenced by the JMenuItems which depict it. So when the JMenuItems
         * are gone, this instance will become obsolete and OmemoWeakListener will
         * remove it as a listener of scOmemoEngine and scOmemoKeyManager.
         */
        new OmemoWeakListener<OtrContactMenu>(
                this,
                OmemoActivator.scOmemoEngine, OmemoActivator.scOtrKeyManager);

        setSessionStatus(
            OmemoActivator.scOmemoEngine.getSessionStatus(this.contact));
        setOmemoPolicy(
            OmemoActivator.scOmemoEngine.getContactPolicy(otrContact.contact));

        buildMenu();
    }

    /*
     * Implements ActionListener#actionPerformed(ActionEvent).
     */
    public void actionPerformed(ActionEvent e)
    {
        String actionCommand = e.getActionCommand();

        if (ACTION_COMMAND_END_OTR.equals(actionCommand))
        {
            OmemoPolicy policy =
                OmemoActivator.scOmemoEngine.getContactPolicy(contact.contact);
            policy.setSendWhitespaceTag(false);
            OmemoActivator.scOmemoEngine.setContactPolicy(contact.contact, policy);

            // End session.
            OmemoActivator.scOmemoEngine.endSession(contact);
        }

        else if (ACTION_COMMAND_START_OTR.equals(actionCommand))
        {
            OmemoPolicy policy =
                OmemoActivator.scOmemoEngine.getContactPolicy(contact.contact);
            OmemoPolicy globalPolicy =
                OmemoActivator.scOmemoEngine.getGlobalPolicy();
            policy.setSendWhitespaceTag(globalPolicy.getSendWhitespaceTag());
            OmemoActivator.scOmemoEngine.setContactPolicy(contact.contact, policy);

            // Start session.
            OmemoActivator.scOmemoEngine.startSession(contact);
        }

        else if (ACTION_COMMAND_REFRESH_OTR.equals(actionCommand))
            // Refresh session.
            OmemoActivator.scOmemoEngine.refreshSession(contact);

        else if (ACTION_COMMAND_AUTHENTICATE_BUDDY.equals(actionCommand))
            // Launch auth buddy dialog.
            SwingOmemoActionHandler.openAuthDialog(contact);

        else if (ACTION_COMMAND_CB_ENABLE.equals(actionCommand))
        {
            OmemoPolicy policy =
                OmemoActivator.scOmemoEngine.getContactPolicy(contact.contact);
            boolean state = ((JCheckBoxMenuItem) e.getSource()).isSelected();

            policy.setEnableManual(state);
            OmemoActivator.scOmemoEngine.setContactPolicy(contact.contact, policy);
        }

        else if (ACTION_COMMAND_CB_AUTO.equals(actionCommand))
        {
            OmemoPolicy policy =
                OmemoActivator.scOmemoEngine.getContactPolicy(contact.contact);
            boolean state = ((JCheckBoxMenuItem) e.getSource()).isSelected();

            policy.setSendWhitespaceTag(state);

            OmemoActivator.scOmemoEngine.setContactPolicy(contact.contact, policy);
        }

        else if (ACTION_COMMAND_CB_AUTO_ALL.equals(actionCommand))
        {
            OmemoPolicy globalPolicy =
                OmemoActivator.scOmemoEngine.getGlobalPolicy();
            boolean state = ((JCheckBoxMenuItem) e.getSource()).isSelected();

            globalPolicy.setSendWhitespaceTag(state);

            OmemoActivator.scOmemoEngine.setGlobalPolicy(globalPolicy);
        }

        else if (ACTION_COMMAND_CB_REQUIRE.equals(actionCommand))
        {
            OmemoPolicy policy =
                OmemoActivator.scOmemoEngine.getContactPolicy(contact.contact);
            boolean state = ((JCheckBoxMenuItem) e.getSource()).isSelected();

            policy.setRequireEncryption(state);
            OmemoActivator.configService.setProperty(
                OmemoActivator.OTR_MANDATORY_PROP,
                Boolean.toString(state));
            OmemoActivator.scOmemoEngine.setContactPolicy(contact.contact, policy);
        }
        else if (ACTION_COMMAND_CB_RESET.equals(actionCommand))
            OmemoActivator.scOmemoEngine.setContactPolicy(contact.contact, null);
    }

    /*
     * Implements ScOmemoEngineListener#contactPolicyChanged(Contact).
     */
    public void contactPolicyChanged(Contact contact)
    {
        // Update the corresponding to the contact menu.
        if (OmemoContactMenu.this.contact != null &&
            contact.equals(OmemoContactMenu.this.contact.contact))
            setOmemoPolicy(OmemoActivator.scOmemoEngine.getContactPolicy(contact));
    }

    /*
     * Implements ScOmemoKeyManagerListener#contactVerificationStatusChanged(
     * Contact).
     */
    public void contactVerificationStatusChanged(OmemoContact otrContact)
    {
        if (otrContact.equals(OmemoContactMenu.this.contact))
            setSessionStatus(
                OmemoActivator.scOmemoEngine.getSessionStatus(otrContact));
    }

    /**
     * Disposes of this instance by making it available for garage collection
     * e.g. removes the listeners it has installed on global instances such as
     * <tt>OmemoActivator#scOmemoEngine</tt> and
     * <tt>OmemoActivator#scOmemoKeyManager</tt>.
     */
    void dispose()
    {
        OmemoActivator.scOmemoEngine.removeListener(this);
        OmemoActivator.scOmemoKeyManager.removeListener(this);
    }

    /*
     * Implements ScOmemoEngineListener#globalPolicyChanged().
     */
    public void globalPolicyChanged()
    {
        setOmemoPolicy(OmemoActivator.scOmemoEngine.getContactPolicy(contact.contact));
    }

    /**
     * Rebuilds own menuitems according to {@link OmemoContactMenu#sessionStatus}
     * and the {@link OmemoPolicy} for {@link OmemoContactMenu#contact}.
     */
    private void buildMenu()
    {
        if(separateMenu != null)
            separateMenu.removeAll();

        OmemoPolicy policy =
            OmemoActivator.scOmemoEngine.getContactPolicy(contact.contact);

        JMenuItem endOmemo = new JMenuItem();
        endOmemo.setText(OmemoActivator.resourceService
            .getI18NString("plugin.omemo.menu.END_OTR"));
        endOmemo.setActionCommand(ACTION_COMMAND_END_OTR);
        endOmemo.addActionListener(this);

        JMenuItem startOmemo = new JMenuItem();
        startOmemo.setText(OmemoActivator.resourceService
            .getI18NString("plugin.omemo.menu.START_OTR"));
        startOmemo.setEnabled(policy.getEnableManual());
        startOmemo.setActionCommand(ACTION_COMMAND_START_OTR);
        startOmemo.addActionListener(this);

        JMenuItem refreshOmemo = new JMenuItem();
        refreshOmemo.setText(OmemoActivator.resourceService
            .getI18NString("plugin.omemo.menu.REFRESH_OTR"));
        refreshOmemo.setEnabled(policy.getEnableManual());
        refreshOmemo.setActionCommand(ACTION_COMMAND_REFRESH_OTR);
        refreshOmemo.addActionListener(this);

        switch (this.sessionStatus)
        {
        case LOADING:
            if (separateMenu != null)
            {
                separateMenu.add(endOmemo);
                separateMenu.add(refreshOmemo);
            }
            else
            {
                parentMenu.add(endOmemo);
                parentMenu.add(refreshOmemo);
            }
            break;

        case ENCRYPTED:
            JMenuItem authBuddy = new JMenuItem();
            authBuddy.setText(OmemoActivator.resourceService
                .getI18NString("plugin.omemo.menu.AUTHENTICATE_BUDDY"));
            authBuddy.setActionCommand(ACTION_COMMAND_AUTHENTICATE_BUDDY);
            authBuddy.addActionListener(this);

            if (separateMenu != null)
            {
                separateMenu.add(endOmemo);
                separateMenu.add(refreshOmemo);
                separateMenu.add(authBuddy);
            }
            else
            {
                parentMenu.add(endOmemo);
                parentMenu.add(refreshOmemo);
                parentMenu.add(authBuddy);
            }

            break;

        case FINISHED:
            if (separateMenu != null)
            {
                separateMenu.add(endOmemo);
                separateMenu.add(refreshOmemo);
            }
            else
            {
                parentMenu.add(endOmemo);
                parentMenu.add(refreshOmemo);
            }
            break;

        case TIMED_OUT:
        case PLAINTEXT:
            if (separateMenu != null)
                separateMenu.add(startOmemo);
            else
                parentMenu.add(startOmemo);

            break;
        }

        JCheckBoxMenuItem cbEnable = new JCheckBoxMenuItem();
        cbEnable.setText(OmemoActivator.resourceService
            .getI18NString("plugin.omemo.menu.CB_ENABLE"));
        cbEnable.setSelected(policy.getEnableManual());
        cbEnable.setActionCommand(ACTION_COMMAND_CB_ENABLE);
        cbEnable.addActionListener(this);

        JCheckBoxMenuItem cbAlways = new JCheckBoxMenuItem();
        cbAlways.setText(String.format(
                OmemoActivator.resourceService
                    .getI18NString(
                        "plugin.omemo.menu.CB_AUTO",
                        new String[]
                            {contact.contact.getDisplayName()})));
        cbAlways.setEnabled(policy.getEnableManual());

        cbAlways.setSelected(policy.getEnableAlways());

        cbAlways.setActionCommand(ACTION_COMMAND_CB_AUTO);
        cbAlways.addActionListener(this);

        JCheckBoxMenuItem cbAlwaysAll = new JCheckBoxMenuItem();
        cbAlwaysAll.setText(OmemoActivator.resourceService
            .getI18NString("plugin.omemo.menu.CB_AUTO_ALL"));
        cbAlwaysAll.setEnabled(policy.getEnableManual());

        boolean isAutoInit =
            OmemoActivator.scOmemoEngine.getGlobalPolicy().getEnableAlways();

        cbAlwaysAll.setSelected(isAutoInit);

        cbAlwaysAll.setActionCommand(ACTION_COMMAND_CB_AUTO_ALL);
        cbAlwaysAll.addActionListener(this);

        JCheckBoxMenuItem cbRequire = new JCheckBoxMenuItem();
        cbRequire.setText(OmemoActivator.resourceService
            .getI18NString("plugin.omemo.menu.CB_REQUIRE"));
        cbRequire.setEnabled(policy.getEnableManual());

        String otrMandatoryPropValue
            = OmemoActivator.configService.getString(
                OmemoActivator.OTR_MANDATORY_PROP);
        String defaultOmemoPropValue
            = OmemoActivator.resourceService.getSettingsString(
                OmemoActivator.OTR_MANDATORY_PROP);

        boolean isMandatory = policy.getRequireEncryption();
        if (otrMandatoryPropValue != null)
            isMandatory = Boolean.parseBoolean(otrMandatoryPropValue);
        else if (!isMandatory && defaultOmemoPropValue != null)
            isMandatory = Boolean.parseBoolean(defaultOmemoPropValue);

        cbRequire.setSelected(isMandatory);

        cbRequire.setActionCommand(ACTION_COMMAND_CB_REQUIRE);
        cbRequire.addActionListener(this);

        JMenuItem cbReset = new JMenuItem();
        cbReset.setText(OmemoActivator.resourceService
            .getI18NString("plugin.omemo.menu.CB_RESET"));
        cbReset.setActionCommand(ACTION_COMMAND_CB_RESET);
        cbReset.addActionListener(this);

        if (separateMenu != null)
        {
            separateMenu.addSeparator();
            separateMenu.add(cbEnable);
            separateMenu.add(cbAlways);
            separateMenu.add(cbAlwaysAll);
            separateMenu.add(cbRequire);
            separateMenu.addSeparator();
            separateMenu.add(cbReset);

            parentMenu.add(separateMenu);
        }
        else
        {
            parentMenu.addSeparator();
            parentMenu.add(cbEnable);
            parentMenu.add(cbAlways);
            parentMenu.add(cbAlwaysAll);
            parentMenu.add(cbRequire);
            parentMenu.addSeparator();
            parentMenu.add(cbReset);
        }
    }

    /*
     * Implements ScOmemoEngineListener#sessionStatusChanged(Contact).
     */
    public void sessionStatusChanged(OmemoContact otrContact)
    {
        if (otrContact.equals(OmemoContactMenu.this.contact))
            setSessionStatus(
                OmemoActivator.scOmemoEngine.getSessionStatus(otrContact));
    }

    /**
     * Sets the {@link OmemoContactMenu#sessionStatus} value, updates the menu
     * icon and, if necessary, rebuilds the menuitems to match the passed in
     * sessionStatus.
     *
     * @param sessionStatus the {@link ScSessionStatus}.
     */
    private void setSessionStatus(ScSessionStatus sessionStatus)
    {
        if (sessionStatus != this.sessionStatus)
        {
            logger.debug(
                "Setting session status of contact " + contact.contact +
                " to " + sessionStatus + ". Was " + this.sessionStatus);
            this.sessionStatus = sessionStatus;

            if (separateMenu != null)
            {
                updateIcon();
                if (separateMenu.isPopupMenuVisible() || inMacOSXScreenMenuBar)
                    buildMenu();
            }
        }
    }

    /**
     * Sets the {@link OmemoContactMenu#otrPolicy} and, if necessary, rebuilds the
     * menuitems to match the passed in otrPolicy.
     *
     * @param otrPolicy
     */
    private void setOmemoPolicy(OmemoPolicy otrPolicy)
    {
        if (!otrPolicy.equals(this.omemoPolicy))
        {
            this.omemoPolicy = otrPolicy;

            if ((separateMenu != null)
                    && (separateMenu.isPopupMenuVisible()
                            || inMacOSXScreenMenuBar))
            {
                buildMenu();
            }
        }
    }

    /**
     * Updates the menu icon based on {@link OmemoContactMenu#sessionStatus}
     * value.
     */
    private void updateIcon()
    {
        if (separateMenu == null)
            return;

        String imageID;

        switch (sessionStatus)
        {
        case ENCRYPTED:
            PublicKey pubKey =
                OmemoActivator.scOmemoEngine.getRemotePublicKey(contact);
            String fingerprint =
                OmemoActivator.scOmemoKeyManager.
                    getFingerprintFromPublicKey(pubKey);
            imageID
                = OmemoActivator.scOmemoKeyManager.isVerified(
                    contact.contact, fingerprint)
                    ? "plugin.omemo.ENCRYPTED_ICON_16x16"
                    : "plugin.omemo.ENCRYPTED_UNVERIFIED_ICON_16x16";
            break;

        case FINISHED:
            imageID = "plugin.omemo.FINISHED_ICON_16x16";
            break;

        case PLAINTEXT:
            imageID = "plugin.omemo.PLAINTEXT_ICON_16x16";
            break;

        default:
            return;
        }

        separateMenu.setIcon(OmemoActivator.resourceService.getImage(imageID));
    }

    @Override
    public void multipleInstancesDetected(OmemoContact contact) {}

    @Override
    public void outgoingSessionChanged(OmemoContact otrContact)
    {
        if (otrContact.equals(OmemoContactMenu.this.contact))
            setSessionStatus(
                OmemoActivator.scOmemoEngine.getSessionStatus(otrContact));
    }
}
