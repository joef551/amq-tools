/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redhat.amq.tools;

import java.util.ArrayList;
import org.apache.activemq.util.IntrospectionSupport;

public final class CommandLineSupport {

	private CommandLineSupport() {
	}

	/**
	 * Sets the properties of an object given the command line args.
	 * 
	 * if args contains: ackmode=AUTO url=tcp://localhost:61616 persistent
	 * 
	 * then it will try to call the following setters on the target object.
	 * 
	 * target.setAckMode("AUTO"); target.setURL(new URI("tcp://localhost:61616")
	 * ); target.setPersistent(true);
	 * 
	 */
	public static String[] setOptions(Object target, String[] args) {
		ArrayList<String> rc = new ArrayList<String>();
		if (args != null && args.length > 0) {
			for (String option : args) {
				if (option != null && !option.isEmpty()) {
					String value = "";
					String name = null;
					String[] tokens = option.trim().split("=");
					name = tokens[0];
					switch (tokens.length) {
					case 1:
						value = "true";
						break;
					case 2:
						value = tokens[1];
						break;
					default:
						for (int i = 1; i < tokens.length; i++) {
							value += tokens[i];
							if (i < tokens.length - 1)
								value += "=";
						}
					}
					if (!IntrospectionSupport.setProperty(target, name, value)) {
						rc.add(option);
						continue;
					}
				}
			}
		}
		String r[] = new String[rc.size()];
		rc.toArray(r);
		return r;
	}
}
