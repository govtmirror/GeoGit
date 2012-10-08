/* Copyright (c) 2011 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the LGPL 2.1 license, available at the root
 * application directory.
 */
package org.geogit.api.porcelain;

import org.geogit.api.AbstractGeoGitOp;
import org.geogit.api.Ref;
import org.geogit.repository.Repository;

/**
 * 
 * Rebase the current head to the included branch head
 * 
 * @author jhudson
 * @since 1.2.0
 */
public class RebaseOp extends AbstractGeoGitOp<Boolean> {

    private Ref branch;

    public RebaseOp(Repository repository) {
        // TODO Auto-generated constructor stub
    }

    public RebaseOp include(final Ref branch) {
        this.branch = branch;
        return this;
    }

    @Override
    public Boolean call() {
        // Ref newRef = new Ref(Ref.HEAD, branch.getObjectId(), TYPE.COMMIT);
        // getRepository().updateRef(newRef);
        // LOGGER.info("Rebased master branch -> " + branch.getName());
        // LOGGER.info(" " + newRef.toString());
        // return true;
        throw new UnsupportedOperationException();
    }
}