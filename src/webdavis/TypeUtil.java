// ========================================================================
// Copyright 2004-2005 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package webdavis;

/* ------------------------------------------------------------ */
/** TYPE Utilities.
 * Provides various static utiltiy methods for manipulating types and their
 * string representations.
 *
 * @since Jetty 4.1
 * @author Greg Wilkins (gregw)
 */
public class TypeUtil
{

    /* ------------------------------------------------------------ */
    /** 
     * @param b An ASCII encoded character 0-9 a-f A-F
     * @return The byte value of the character 0-16.
     */
    public static byte convertHexDigit( byte b )
    {
        if ((b >= '0') && (b <= '9')) return (byte)(b - '0');
        if ((b >= 'a') && (b <= 'f')) return (byte)(b - 'a' + 10);
        if ((b >= 'A') && (b <= 'F')) return (byte)(b - 'A' + 10);
        return 0;
    }


    

}
