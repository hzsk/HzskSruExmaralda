#!/bin/bash
if test $# -lt 2 ; then
    echo Usage: $0 URL QUERY
    echo
    echo Tests FCS server at URL that has more than 0 hits for QUERY
    echo URL must be SRU address, QUERY must be simple search term
    exit 2
fi
if ! which lwp-request ; then
    echo This tester needs libwww-perl command line tools
    exit 2
fi
URL=$1
QUERY=$2
WORK=$(mktemp -d -t fcs2-tester.XXXXXXXXXX)
echo GET $URL
if ! lwp-request $URL > $WORK/get ; then
    echo failed to GET $URL
    cat $WORK/get
    exit 1
else
    xmllint -format $WORK/get
fi
params="?operation=explain"
echo GET "$URL$params"
if ! lwp-request "$URL$params" > $WORK/explain ; then
    echo failed to GET "$URL$params"
    cat $WORK/explain
    exit 1
else
    xmllint -format $WORK/explain
fi
params="?operation=explain&version=1.2&x-fcs-endpoint-description=true"
echo GET "$URL$params"
if ! lwp-request "$URL$params" > $WORK/explainFcs2 ; then
    echo failed to GET "$URL$params"
    cat $WORK/explainFcs2
    exit 1
else
    xmllint -format $WORK/explainFcs2
fi
params="?operation=searchRetrieve&query=$QUERY"
echo GET "$URL$params"
if ! lwp-request "$URL$params" > $WORK/searchRetrieve ; then
    echo failed to GET "$URL$params"
    cat $WORK/searchRetrieve
    exit 1
else
    fgrep -c Result $WORK/searchRetrieve
fi
params="?operation=searchRetrieve&queryType=fcs&query=%5lemma%3d\"$QUERY\"%5d"
echo GET "$URL$params"
if ! lwp-request "$URL$params" > $WORK/searchRetrieve ; then
    echo failed to GET "$URL$params"
    cat $WORK/searchRetrieve
    exit 1
else
    fgrep -c Result $WORK/searchRetrieve
fi
params="?operation=searchRetrieve&queryType=fcs&query=%5bword%3d\"$QUERY\"%5d"
echo GET "$URL$params"
if ! lwp-request "$URL$params" > $WORK/searchRetrieve ; then
    echo failed to GET "$URL$params"
    cat $WORK/searchRetrieve
    exit 1
else
    fgrep -c Result $WORK/searchRetrieve
fi
params="?operation=searchRetrieve&queryType=fcs&query=%5bword%3d\"$QUERY\"%5d&x-cmd-resource-info=true"
echo GET "$URL$params"
if ! lwp-request "$URL$params" > $WORK/searchRetrieve ; then
    echo failed to GET "$URL$params"
    cat $WORK/searchRetrieve
    exit 1
else
    fgrep -c Result $WORK/searchRetrieve
fi

