/*==LICENSE==*

CyanWorlds.com Engine - MMOG client, server and tools
Copyright (C) 2011  Cyan Worlds, Inc.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Additional permissions under GNU GPL version 3 section 7

If you modify this Program, or any covered work, by linking or
combining it with any of RAD Game Tools Bink SDK, Autodesk 3ds Max SDK,
NVIDIA PhysX SDK, Microsoft DirectX SDK, OpenSSL library, Independent
JPEG Group JPEG library, Microsoft Windows Media SDK, or Apple QuickTime SDK
(or a modified version of those libraries),
containing parts covered by the terms of the Bink SDK EULA, 3ds Max EULA,
PhysX SDK EULA, DirectX SDK EULA, OpenSSL and SSLeay licenses, IJG
JPEG Library README, Windows Media SDK EULA, or QuickTime SDK EULA, the
licensors of this Program grant you additional
permission to convey the resulting work. Corresponding Source for a
non-source form of such a combination shall include the source code for
the parts of OpenSSL and IJG JPEG Library used as well as that of the covered
work.

You can contact Cyan Worlds, Inc. by email legal@cyan.com
 or by snail mail at:
      Cyan Worlds, Inc.
      14617 N Newport Hwy
      Mead, WA   99021

*==LICENSE==*/
//////////////////////////////////////////////////////////////////////////////
//                                                                          //
//  plProgressMgr Functions                                                 //
//                                                                          //
//// History /////////////////////////////////////////////////////////////////
//                                                                          //
//  10.26.2001 mcn  - Created                                               //
//                                                                          //
//////////////////////////////////////////////////////////////////////////////

#include "HeadSpin.h"
#include "plProgressMgr.h"
#include "hsTimer.h"

#include "plPipeline/plPlates.h"


//////////////////////////////////////////////////////////////////////////////
//// plProgressMgr Functions /////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

plProgressMgr   *plProgressMgr::fManager = nil;

#define LOADING_RES         "xLoading_Linking.%02d.png"
#define LOADING_RES_COUNT   18

char* plProgressMgr::fImageRotation[LOADING_RES_COUNT];

const char* plProgressMgr::fStaticTextIDs[] = {
    "xLoading_Linking_Text.png",
    "xLoading_Updating_Text.png"
};

//// Constructor & Destructor ////////////////////////////////////////////////

plProgressMgr::plProgressMgr()
{
    fOperations = nil;
    fManager = this;
    fCallbackProc = nil;
    fCurrentStaticText = kNone;

    // Fill array with pre-computed loading frame IDs
    for (int i=0; i < LOADING_RES_COUNT; i++)
    {
        char* frameID = new char[128];
        sprintf(frameID, LOADING_RES, i);
        fImageRotation[i] = frameID;
    }
}

plProgressMgr::~plProgressMgr()
{
    for (int i=0; i < LOADING_RES_COUNT; i++)
    {
        delete fImageRotation[i];
    }

    while( fOperations != nil )
        delete fOperations;
    fManager = nil;
}

//// RegisterOperation ///////////////////////////////////////////////////////

plOperationProgress* plProgressMgr::RegisterOperation(float length, const char *title, StaticText staticTextType, bool isRetry, bool alwaysDrawText)
{
    return IRegisterOperation(length, title, staticTextType, isRetry, false, alwaysDrawText);
}

plOperationProgress* plProgressMgr::RegisterOverallOperation(float length, const char *title, StaticText staticTextType, bool alwaysDrawText)
{
    return IRegisterOperation(length, title, staticTextType, false, true, alwaysDrawText);
}

plOperationProgress* plProgressMgr::IRegisterOperation(float length, const char *title, StaticText staticTextType, bool isRetry, bool isOverall, bool alwaysDrawText)
{
    if (fOperations == nil)
    {
        fCurrentStaticText = staticTextType;
        Activate();
    }

    plOperationProgress *op = new plOperationProgress( length );

    op->SetTitle( title );

    if (fOperations)
    {
        fOperations->fBack = op;
        op->fNext = fOperations;
    }
    fOperations = op;

    if (isRetry)
        hsSetBits(op->fFlags, plOperationProgress::kRetry);
    if (isOverall)
        hsSetBits(op->fFlags, plOperationProgress::kOverall);
    if (alwaysDrawText)
        hsSetBits(op->fFlags, plOperationProgress::kAlwaysDrawText);

    IUpdateCallbackProc( op );

    return op;
}

void plProgressMgr::IUnregisterOperation(plOperationProgress* op)
{
    plOperationProgress* last = nil;
    plOperationProgress* cur = fOperations;

    while (cur)
    {
        if (cur == op)
        {
            if (cur->fNext)
                cur->fNext->fBack = last;

            if (last)
                last->fNext = cur->fNext;
            else
                fOperations = cur->fNext;

            break;
        }

        last = cur;
        cur = cur->fNext;
    }

    if (fOperations == nil)
    {
        fCurrentStaticText = kNone;
        Deactivate();
    }
}

//// IUpdateCallbackProc /////////////////////////////////////////////////////

void plProgressMgr::IUpdateFlags(plOperationProgress* progress)
{
    // Init update is done, clear it and set first update
    if (hsCheckBits(progress->fFlags, plOperationProgress::kInitUpdate))
    {
        hsClearBits(progress->fFlags, plOperationProgress::kInitUpdate);
        hsSetBits(progress->fFlags, plOperationProgress::kFirstUpdate);
    }
    // First update is done, clear it
    else if (hsCheckBits(progress->fFlags, plOperationProgress::kFirstUpdate))
        hsClearBits(progress->fFlags, plOperationProgress::kFirstUpdate);
}

void plProgressMgr::IUpdateCallbackProc(plOperationProgress* progress)
{
    // Update the parent, if necessary
    plOperationProgress* parentProgress = progress->GetNext();
    while (parentProgress && parentProgress->IsOverallProgress())
    {
        parentProgress->IChildUpdateBegin(progress);
        parentProgress = parentProgress->GetNext();
    }

    // Update everyone who wants to know about progress
    IDerivedCallbackProc(progress);
    if (fCallbackProc != nil)
        fCallbackProc(progress);

    IUpdateFlags(progress);

    parentProgress = progress->GetNext();
    while (parentProgress && parentProgress->IsOverallProgress())
    {
        parentProgress->IChildUpdateEnd(progress);
        parentProgress = parentProgress->GetNext();
    }
}

//// SetCallbackProc /////////////////////////////////////////////////////////

plProgressMgrCallbackProc plProgressMgr::SetCallbackProc( plProgressMgrCallbackProc proc )
{
    plProgressMgrCallbackProc old = fCallbackProc;
    fCallbackProc = proc;
    return old;
}

//// CancelAllOps ////////////////////////////////////////////////////////////

void    plProgressMgr::CancelAllOps( void )
{
    plOperationProgress *op;


    for( op = fOperations; op != nil; op = op->GetNext() )
        op->SetCancelFlag( true );

    fCurrentStaticText = kNone;
}

char*   plProgressMgr::GetLoadingFrameID(int index)
{
    if (index < LOADING_RES_COUNT)
        return fImageRotation[index];
    else
        return fImageRotation[0];
}

const char*   plProgressMgr::GetStaticTextID(StaticText staticTextType)
{
    return fStaticTextIDs[staticTextType];
}


//////////////////////////////////////////////////////////////////////////////
//// plOperationProgress ////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

plOperationProgress::plOperationProgress( float length ) :
    fMax(length),
    fValue(0),
    fNext(nil),
    fBack(nil),
    fContext(0),
    fFlags(kInitUpdate),
    fStartTime(hsTimer::GetSeconds()),
    fElapsedSecs(0),
    fRemainingSecs(0),
    fAmtPerSec(0.f)
{
}

plOperationProgress::~plOperationProgress()
{
    hsSetBits(fFlags, kLastUpdate);
    if (!IsOverallProgress())
        plProgressMgr::GetInstance()->IUpdateCallbackProc(this);
    plProgressMgr::GetInstance()->IUnregisterOperation(this);
}

void plOperationProgress::IUpdateStats()
{
    double curTime = hsTimer::GetSeconds();
    double elapsed = 0;
    if (curTime > fStartTime)
        elapsed = curTime - fStartTime;
    else
        elapsed = fStartTime - curTime;

    float progress = GetProgress();

    if (elapsed > 0)
        fAmtPerSec = progress / float(elapsed);
    else
        fAmtPerSec = 0;
    fElapsedSecs = (uint32_t)elapsed;
    if (progress < fMax)
        fRemainingSecs = (uint32_t)((fMax - progress) / fAmtPerSec);
    else
        fRemainingSecs = 0;
}

void plOperationProgress::IChildUpdateBegin(plOperationProgress* child)
{
    if (child->IsFirstUpdate() && child->IsRetry())
    {
        // We're retrying this file, so update the overall stats to reflect the additional download
        fMax += child->GetMax();
    }
    fValue += child->GetProgress();

    IUpdateStats();
}

void plOperationProgress::IChildUpdateEnd(plOperationProgress* child)
{
    // If we're aborting, modify the total bytes to reflect any data we didn't download
    if (hsCheckBits(child->fFlags, plOperationProgress::kAborting))
        fMax += child->GetProgress() - child->GetMax();
    else if (!child->IsLastUpdate())
        fValue -= child->GetProgress();
}

//// Increment ///////////////////////////////////////////////////////////////

void    plOperationProgress::Increment( float byHowMuch )
{
    fValue += byHowMuch;
    if( fValue > fMax )
        fValue = fMax;
    IUpdateStats();

    plProgressMgr::GetInstance()->IUpdateCallbackProc( this );
}

//// SetHowMuch //////////////////////////////////////////////////////////////

void    plOperationProgress::SetHowMuch( float howMuch )
{
    fValue = howMuch;
    if( fValue > fMax )
        fValue = fMax;
    IUpdateStats();

    plProgressMgr::GetInstance()->IUpdateCallbackProc( this );
}

//// SetLength ///////////////////////////////////////////////////////////////

void    plOperationProgress::SetLength( float length )
{
    fMax = length;
    if( fValue > fMax )
        fValue = fMax;
    IUpdateStats();

    plProgressMgr::GetInstance()->IUpdateCallbackProc( this );
}

void plOperationProgress::SetAborting()
{
    hsSetBits(fFlags, kAborting);
    plProgressMgr::GetInstance()->IUpdateCallbackProc(this);
    fMax = fValue = 0.f;
}

void plOperationProgress::SetRetry()
{
    hsSetBits(fFlags, kRetry);
    hsSetBits(fFlags, kFirstUpdate);
}
