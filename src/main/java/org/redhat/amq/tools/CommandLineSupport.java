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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
					//System.out.println("Checking " + name + ":" + value);
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

	public static ArrayList<String> readProps(String fileName) throws Exception {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(new File(fileName)));
		} catch (Exception e) {
			System.out.println("ERROR: Unable to open props file");
			e.printStackTrace();
			System.exit(0);
		}
		ArrayList<String> props = new ArrayList<String>();
		// start reading in the properties
		String line = null;
		while ((line = reader.readLine()) != null) {
			// ignore empty lines and lines that start with '#'
			line = line.trim();
			if (line.length() == 0 || line.startsWith("#")) {
				continue;
			}
			props.add(line.trim());
		}
		reader.close();
		return props;

	}
}
