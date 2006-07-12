/*

   Derby - Class org.apache.derby.impl.sql.execute.RoutinePrivilegeInfo

   Copyright 1998, 2005 The Apache Software Foundation or its licensors, as applicable.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

	  http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.impl.sql.execute;

import org.apache.derby.iapi.sql.Activation;
import org.apache.derby.iapi.services.sanity.SanityManager;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.store.access.TransactionController;
import org.apache.derby.iapi.sql.dictionary.RoutinePermsDescriptor;
import org.apache.derby.iapi.sql.dictionary.AliasDescriptor;
import org.apache.derby.iapi.sql.dictionary.StatementRoutinePermission;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.DataDescriptorGenerator;
import org.apache.derby.iapi.error.StandardException;

import java.util.Iterator;
import java.util.List;

public class RoutinePrivilegeInfo extends PrivilegeInfo
{
	private AliasDescriptor aliasDescriptor;

	public RoutinePrivilegeInfo( AliasDescriptor aliasDescriptor)
	{
		this.aliasDescriptor = aliasDescriptor;
	}
	
	/**
	 *	This is the guts of the Execution-time logic for GRANT/REVOKE of a routine execute privilege
	 *
	 * @param activation
	 * @param grant true if grant, false if revoke
	 * @param grantees a list of authorization ids (strings)
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public void executeGrantRevoke( Activation activation,
									boolean grant,
									List grantees)
		throws StandardException
	{
		// Check that the current user has permission to grant the privileges.
		LanguageConnectionContext lcc = activation.getLanguageConnectionContext();
		DataDictionary dd = lcc.getDataDictionary();
		String currentUser = lcc.getAuthorizationId();
		TransactionController tc = lcc.getTransactionExecute();

		// Check that the current user has permission to grant the privileges.
		checkOwnership( currentUser,
						aliasDescriptor,
						dd.getSchemaDescriptor( aliasDescriptor.getSchemaUUID(), tc),
						dd);
		
		DataDescriptorGenerator ddg = dd.getDataDescriptorGenerator();

		RoutinePermsDescriptor routinePermsDesc = ddg.newRoutinePermsDescriptor( aliasDescriptor, currentUser);

		dd.startWriting(lcc);
		for( Iterator itr = grantees.iterator(); itr.hasNext();)
		{
			// It is possible for grant statement to look like following
			//   grant execute on function f_abs to mamata2, mamata3;
			// This means that dd.addRemovePermissionsDescriptor will be called
			// twice for routinePermsDesc, once for each grantee.
			// First it's called for mamta2. After a row is inserted for mamta2 
			// into SYS.SYSROUTINEPERMS, the routinePermsDesc's uuid will get 
			// populated with the uuid of the row that just got inserted into 
			// SYS.SYSROUTINEPERMS for mamta2
			// Next, before dd.addRemovePermissionsDescriptor gets called for 
			// MAMTA3, we should set the routinePermsDesc's uuid to null or 
			// otherwise, we will think that there is a duplicate row getting
			// inserted for the same uuid.
			String grantee = (String) itr.next();
            if(grant)
            	routinePermsDesc.setUUID(null);
			dd.addRemovePermissionsDescriptor( grant, routinePermsDesc, grantee, tc);
		}
	} // end of executeConstantAction
}
