/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Copyright @ 2016 Atlassian Pty Ltd
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
package net.java.sip.communicator.impl.osdependent;

import net.java.sip.communicator.service.systray.*;

/**
 * In case no system-tray is available, or the user chooses not to use it
 * for this application, we can use this implementation of the
 * <code>SystrayService</code> interface, which makes the application work
 * in the most simple way: if window is open, the application is running,
 * if window is closed, the application is shut-down.
 *
 * @author hoijui
 */
public class NoneSystrayServiceImpl
    extends AbstractSystrayService
    implements SystrayService
{

    /**
     * Initializes a new <tt>NoneSystrayServiceImpl</tt> instance.
     */
    public NoneSystrayServiceImpl()
    {
        super(OsDependentActivator.bundleContext); // TODO this is not the right context! don't know where to find or reate that one
    }

    public void setSystrayIcon(int imageType)
    {
        // Do nothing
    }

    public boolean isSupported()
    {
        return true;
    }
}
