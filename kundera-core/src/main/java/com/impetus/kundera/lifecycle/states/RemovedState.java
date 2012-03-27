/*******************************************************************************
 * * Copyright 2012 Impetus Infotech.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 ******************************************************************************/
package com.impetus.kundera.lifecycle.states;


import com.impetus.kundera.lifecycle.NodeStateContext;

/**
 * @author amresh
 *
 */
public class RemovedState extends NodeState
{
    @Override
    public void initialize(NodeStateContext nodeStateContext)
    {
    }

    @Override
    public void handlePersist(NodeStateContext nodeStateContext)
    {
        nodeStateContext.setCurrentNodeState(new ManagedState());
        //TODO: Recurse persist operation on all related entities for whom cascade=ALL or PERSIST
    }    

    @Override
    public void handleRemove(NodeStateContext nodeStateContext)
    {
        //Ignored, entity will remain in removed state
        //TODO: Recurse remove operation for all related entities for whom cascade=ALL or REMOVE
    }

    @Override
    public void handleRefresh(NodeStateContext nodeStateContext)
    {
      //Ignored, entity will remain in removed state
      //TODO: Cascade refresh operation for all related entities for whom cascade=ALL or REFRESH
    }

    @Override
    public void handleMerge(NodeStateContext nodeStateContext)
    {
        throw new IllegalArgumentException("Merge operation not allowed in Removed state");
    }
    
    @Override
    public void handleFind(NodeStateContext nodeStateContext)
    {
    }

    @Override
    public void handleClose(NodeStateContext nodeStateContext)
    {
    }

    @Override
    public void handleClear(NodeStateContext nodeStateContext)
    {
    }

    @Override
    public void handleFlush(NodeStateContext nodeStateContext)
    {
    }

    @Override
    public void handleLock(NodeStateContext nodeStateContext)
    {
    }

    @Override
    public void handleDetach(NodeStateContext nodeStateContext)
    {
    }

    @Override
    public void handleCommit(NodeStateContext nodeStateContext)
    {
        nodeStateContext.setCurrentNodeState(new TransientState());
    }

    @Override
    public void handleRollback(NodeStateContext nodeStateContext)
    {
        //If Persistence Context is EXTENDED
        nodeStateContext.setCurrentNodeState(new ManagedState());
        
        //If Persistence Context is TRANSACTIONAL
        //context.setCurrentEntityState(new DetachedState());
    }

    @Override
    public void handleGetReference(NodeStateContext nodeStateContext)
    {
    }

    @Override
    public void handleContains(NodeStateContext nodeStateContext)
    {
    }   
    
    
}