/* Copyright (c) 2013 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */

package org.geogit.cli.plumbing;

import static com.google.common.base.Preconditions.checkState;

import java.util.Iterator;

import org.geogit.api.Ref;
import org.geogit.api.RevCommit;
import org.geogit.api.porcelain.BranchListOp;
import org.geogit.api.porcelain.LogOp;
import org.geogit.cli.AbstractCommand;
import org.geogit.cli.CLICommand;
import org.geogit.cli.GeogitCLI;

import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;

/**
 *
 */
@Parameters(commandNames = "rebuild-graph", commandDescription = "Rebuilds the geogit graph database.")
public class RebuildGraph extends AbstractCommand implements CLICommand {

    @Override
    public void runInternal(GeogitCLI cli) throws Exception {
        checkState(cli.getGeogit() != null, "Not a geogit repository: " + cli.getPlatform().pwd());
        ImmutableList<Ref> branches = cli.getGeogit().command(BranchListOp.class).setLocal(true)
                .setRemotes(true).call();

        for (Ref ref : branches) {
            Iterator<RevCommit> commits = cli.getGeogit().command(LogOp.class)
                    .setUntil(ref.getObjectId()).call();
            while (commits.hasNext()) {
                RevCommit next = commits.next();
                cli.getGeogit().getRepository().getGraphDatabase()
                        .put(next.getId(), next.getParentIds());
            }
        }
    }
}
