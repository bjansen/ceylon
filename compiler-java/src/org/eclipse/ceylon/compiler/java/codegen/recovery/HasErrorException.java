/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.eclipse.ceylon.compiler.java.codegen.recovery;

import org.eclipse.ceylon.compiler.typechecker.tree.Message;
import org.eclipse.ceylon.compiler.typechecker.tree.Node;

@SuppressWarnings("serial")
public class HasErrorException 
        extends RuntimeException implements LocalizedError {

    private final Message message;
    private final Node node;
    
    public HasErrorException(Node that, Message message) {
        this.node = that;
        this.message = message;
    }
    
    public Message getErrorMessage() {
        return message;
    }
    
    public Node getNode() {
        return node;
    }
}
