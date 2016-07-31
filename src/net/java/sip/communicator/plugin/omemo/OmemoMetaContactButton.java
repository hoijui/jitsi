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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.security.*;

import javax.imageio.*;

import net.jomemo.*;
import net.jomemo.session.*;
import net.java.sip.communicator.plugin.desktoputil.*;
import net.java.sip.communicator.plugin.omemo.OmemoContactManager.OmemoContact;
import net.java.sip.communicator.service.contactlist.*;
import net.java.sip.communicator.service.gui.*;
import net.java.sip.communicator.service.gui.Container;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;

/**
 * A {@link AbstractPluginComponent} that registers the Off-the-Record button in
 * the main chat toolbar.
 *
 * @author George Politis
 * @author Marin Dzhigarov
 */
public class OmemoMetaContactButton
    extends AbstractPluginComponent
    implements ScOmemoEngineListener,
               ScOmemoKeyManagerListener
{
    /**
     * The logger
     */
    private final Logger logger = Logger.getLogger(OmemoMetaContactButton.class);

    private SIPCommButton button;

    private OmemoContact otrContact;

    private AnimatedImage animatedPadlockImage;

    private Image finishedPadlockImage;

    private Image verifiedLockedPadlockImage;

    private Image unverifiedLockedPadlockImage;

    private Image unlockedPadlockImage;

    private Image timedoutPadlockImage;

    public void sessionStatusChanged(OmemoContact otrContact)
    {
        // OmemoMetaContactButton.this.contact can be null.
        if (otrContact.equals(OmemoMetaContactButton.this.omemoContact))
        {
            setStatus(
                OmemoActivator.scOmemoEngine.getSessionStatus(otrContact));
        }
    }

    public void contactPolicyChanged(Contact contact)
    {
        // OmemoMetaContactButton.this.contact can be null.
        if (OmemoMetaContactButton.this.omemoContact != null &&
            contact.equals(OmemoMetaContactButton.this.omemoContact.contact))
        {
            setPolicy(
                OmemoActivator.scOmemoEngine.getContactPolicy(contact));
        }
    }

    public void globalPolicyChanged()
    {
        if (OmemoMetaContactButton.this.omemoContact != null)
            setPolicy(
                OmemoActivator.scOmemoEngine.getContactPolicy(otrContact.contact));
    }

    public void contactVerificationStatusChanged(OmemoContact otrContact)
    {
        // OmemoMetaContactButton.this.contact can be null.
        if (otrContact.equals(OmemoMetaContactButton.this.omemoContact))
        {
            setStatus(
                OmemoActivator.scOmemoEngine.getSessionStatus(otrContact));
        }
    }

    public OmemoMetaContactButton(Container container,
                                PluginComponentFactory parentFactory)
    {
        super(container, parentFactory);

        /*
         * XXX This OmemoMetaContactButton instance cannot be added as a listener
         * to scOmemoEngine and scOmemoKeyManager without being removed later on
         * because the latter live forever. Unfortunately, the dispose() method
         * of this instance is never executed. OmemoWeakListener will keep this
         * instance as a listener of scOmemoEngine and scOmemoKeyManager for as long
         * as this instance is necessary. And this instance will be strongly
         * referenced by the JMenuItems which depict it. So when the JMenuItems
         * are gone, this instance will become obsolete and OmemoWeakListener will
         * remove it as a listener of scOmemoEngine and scOmemoKeyManager.
         */
        new OmemoWeakListener<OtrMetaContactButton>(
            this,
            OmemoActivator.scOmemoEngine, OmemoActivator.scOtrKeyManager);
    }

    /**
     * Gets the <code>SIPCommButton</code> which is the component of this
     * plugin. If the button doesn't exist, it's created.
     *
     * @return the <code>SIPCommButton</code> which is the component of this
     *         plugin
     */
    @SuppressWarnings("fallthrough")
    private SIPCommButton getButton()
    {
        if (button == null)
        {
            button = new SIPCommButton(null, null);
            button.setEnabled(false);
            button.setPreferredSize(new Dimension(25, 25));

            button.setToolTipText(OmemoActivator.resourceService.getI18NString(
                "plugin.omemo.menu.OTR_TOOLTIP"));

            Image i1 = null, i2 = null, i3 = null;
            try
            {
                i1 = ImageIO.read(
                        OmemoActivator.resourceService.getImageURL(
                            "plugin.omemo.LOADING_ICON1_22x22"));
                i2 = ImageIO.read(
                        OmemoActivator.resourceService.getImageURL(
                            "plugin.omemo.LOADING_ICON2_22x22"));
                i3 = ImageIO.read(
                        OmemoActivator.resourceService.getImageURL(
                            "plugin.omemo.LOADING_ICON3_22x22"));
                finishedPadlockImage = ImageIO.read(
                        OmemoActivator.resourceService.getImageURL(
                            "plugin.omemo.FINISHED_ICON_22x22"));
                verifiedLockedPadlockImage = ImageIO.read(
                        OmemoActivator.resourceService.getImageURL(
                            "plugin.omemo.ENCRYPTED_ICON_22x22"));
                unverifiedLockedPadlockImage = ImageIO.read(
                        OmemoActivator.resourceService.getImageURL(
                            "plugin.omemo.ENCRYPTED_UNVERIFIED_ICON_22x22"));
                unlockedPadlockImage = ImageIO.read(
                        OmemoActivator.resourceService.getImageURL(
                            "plugin.omemo.PLAINTEXT_ICON_22x22"));
                timedoutPadlockImage = ImageIO.read(
                        OmemoActivator.resourceService.getImageURL(
                            "plugin.omemo.BROKEN_ICON_22x22"));
            } catch (IOException e)
            {
                logger.debug("Failed to load padlock image");
            }

            animatedPadlockImage = new AnimatedImage(button, i1, i2, i3);

            button.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    if (otrContact == null)
                        return;

                    switch (OmemoActivator.scOmemoEngine.getSessionStatus(otrContact))
                    {
                    case ENCRYPTED:
                        OmemoPolicy policy =
                            OmemoActivator.scOmemoEngine.getContactPolicy(
                                otrContact.contact);
                        policy.setSendWhitespaceTag(false);
                        OmemoActivator.scOmemoEngine.setContactPolicy(
                            otrContact.contact, policy);
                    case FINISHED:
                    case LOADING:
                        // Default action for finished, encrypted and loading
                        // sessions is end session.
                        OmemoActivator.scOmemoEngine.endSession(otrContact);
                        break;
                    case TIMED_OUT:
                    case PLAINTEXT:
                        policy =
                            OmemoActivator.scOmemoEngine.getContactPolicy(
                                otrContact.contact);
                        OmemoPolicy globalPolicy =
                            OmemoActivator.scOmemoEngine.getGlobalPolicy();
                        policy.setSendWhitespaceTag(
                            globalPolicy.getSendWhitespaceTag());
                        OmemoActivator.scOmemoEngine.setContactPolicy(
                            otrContact.contact, policy);
                        // Default action for timed_out and plaintext sessions
                        // is start session.
                        OmemoActivator.scOmemoEngine.startSession(otrContact);
                        break;
                    }
                }
            });
        }
        return button;
    }

    /*
     * Implements PluginComponent#getComponent(). Returns the SIPCommButton
     * which is the component of this plugin creating it first if it doesn't
     * exist.
     */
    public Object getComponent()
    {
        return getButton();
    }

    /*
     * Implements PluginComponent#getName().
     */
    public String getName()
    {
        return "";
    }

    /*
     * Implements PluginComponent#setCurrentContact(Contact).
     */
    @Override
    public void setCurrentContact(Contact contact)
    {
        setCurrentContact(contact, null);
    }

    public void setCurrentContact(Contact contact, String resourceName)
    {
        if (contact == null)
        {
            this.omemoContact = null;
            this.setPolicy(null);
            this.setStatus(ScSessionStatus.PLAINTEXT);
            return;
        }

        if (resourceName == null)
        {
            OmemoContact otrContact =
                OmemoContactManager.getOtrContact(contact, null);
            if (this.omemoContact == otrContact)
                return;
            this.omemoContact = otrContact;
            this.setStatus(
                OmemoActivator.scOmemoEngine.getSessionStatus(otrContact));
            this.setPolicy(
                OmemoActivator.scOmemoEngine.getContactPolicy(contact));
            return;
        }
        for (ContactResource resource : contact.getResources())
        {
            if (resource.getResourceName().equals(resourceName))
            {
                OmemoContact otrContact =
                    OmemoContactManager.getOtrContact(contact, resource);
                if (this.omemoContact == otrContact)
                    return;
                this.omemoContact = otrContact;
                this.setStatus(
                    OmemoActivator.scOmemoEngine.getSessionStatus(otrContact));
                this.setPolicy(
                    OmemoActivator.scOmemoEngine.getContactPolicy(contact));
                return;
            }
        }
        logger.debug("Could not find resource for contact " + contact);
    }

    /*
     * Implements PluginComponent#setCurrentContact(MetaContact).
     */
    @Override
    public void setCurrentContact(MetaContact metaContact)
    {
        setCurrentContact((metaContact == null) ? null : metaContact
            .getDefaultContact());
    }

    /**
     * Sets the button enabled status according to the passed in
     * {@link OmemoPolicy}.
     *
     * @param contactPolicy the {@link OmemoPolicy}.
     */
    private void setPolicy(OmemoPolicy contactPolicy)
    {
        getButton().setEnabled(
            contactPolicy != null && contactPolicy.getEnableManual());
    }

    /**
     * Sets the button icon according to the passed in {@link SessionStatus}.
     *
     * @param status the {@link SessionStatus}.
     */
    private void setStatus(ScSessionStatus status)
    {
        animatedPadlockImage.pause();
        Image image;
        String tipKey;
        switch (status)
        {
        case ENCRYPTED:
            PublicKey pubKey =
                OmemoActivator.scOmemoEngine.getRemotePublicKey(otrContact);
            String fingerprint =
                OmemoActivator.scOmemoKeyManager.
                    getFingerprintFromPublicKey(pubKey);
            image
                = OmemoActivator.scOmemoKeyManager.isVerified(
                        otrContact.contact, fingerprint)
                    ? verifiedLockedPadlockImage
                    : unverifiedLockedPadlockImage;
            tipKey =
                OmemoActivator.scOmemoKeyManager.isVerified(
                        otrContact.contact, fingerprint)
                ? "plugin.omemo.menu.VERIFIED"
                : "plugin.omemo.menu.UNVERIFIED";
            break;
        case FINISHED:
            image = finishedPadlockImage;
            tipKey = "plugin.omemo.menu.FINISHED";
            break;
        case PLAINTEXT:
            image = unlockedPadlockImage;
            tipKey = "plugin.omemo.menu.START_OTR";
            break;
        case LOADING:
            image = animatedPadlockImage;
            animatedPadlockImage.start();
            tipKey = "plugin.omemo.menu.LOADING_OTR";
            break;
        case TIMED_OUT:
            image = timedoutPadlockImage;
            tipKey = "plugin.omemo.menu.TIMED_OUT";
            break;
        default:
            return;
        }

        SIPCommButton button = getButton();
        button.setIconImage(image);
        button.setToolTipText(OmemoActivator.resourceService
            .getI18NString(tipKey));
        button.repaint();
    }

    @Override
    public void multipleInstancesDetected(OmemoContact contact)
    {}

    @Override
    public void outgoingSessionChanged(OmemoContact otrContact)
    {
        // OmemoMetaContactButton.this.contact can be null.
        if (otrContact.equals(OmemoMetaContactButton.this.omemoContact))
        {
            setStatus(
                OmemoActivator.scOmemoEngine.getSessionStatus(otrContact));
        }
    }
}
