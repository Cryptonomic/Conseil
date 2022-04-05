source scanner.fish
source interpreter.fish
source utils.fish
source fetch.fish
source operations.fish
source system.fish

function check
    set -gx TRUE true
    set -gx FALSE false

    function init
        set -gx QTITLE (echo $argv | jq -c '.title')
        set -gx QPATH (echo $argv | jq  -c '.path' | remove-double-qoutes )
        set -gx QUERY (echo $argv | jq -c '.query')
        set -gx CHECKS (echo $argv | jq -c '.check[]')
        set -gx NODEPATH (echo $argv | jq -c '.check[].node')
        set -gx OPERATIONS (echo $argv | jq -c '.check[].operation')
        set -gx CHAIN tezos
        #breakpoint
    end

    getopts $argv | while read -l key value
        switch $key
            case f file
                set queryFile $value
            case i index
                set queryIdx $value
                set selectedJsonObject (cat $queryFile | jq -c ".[$queryIdx]")
                init $selectedJsonObject
            case h host
                set -gx CHOST $value
            case N node
                set -gx NHOST $value
            case n network
                set -gx CNETWORK $value
            case k key
                set -gx apiKey $value

        end
    end

    for i in (stepLength -f $CHECKS)

        if not set -q parsedQueryPath
            set -gx parsedQueryPath (scan-line $QPATH)
            set -gx isQueryDependentOnNode (dependency -a $parsedQueryPath -t N)
            set -gx isQueryDependentOnSystem (dependency -a $parsedQueryPath -t S)
            if test $isQueryDependentOnNode = $FALSE
                if test $isQueryDependentOnSystem = $TRUE
                    setConseilLevel
                end
                set path (interpretAST $parsedQueryPath \
		    | remove-double-qoutes )

                if not test $isQueryDependentOnSystem = $FALSE
                    setConseilLevel
                end
                set -gx conseilResult (post -p $path -q "$QUERY")

            end
        end

        set -gx thisCheck $CHECKS[$i]


        set -gx NODEPATH (echo $thisCheck | jq -c '.node' | remove-double-qoutes)
        set -gx parsedNodePath (scan-line $NODEPATH)
        # breakpoint
        set isNodeDependentOnQuery (dependency -a $parsedNodePath -t Q)
        #breakpoint
        if test $isQueryDependentOnNode = $TRUE
            echo Query has Node Dependence
            if test $isNodeDependentOnQuery = $TRUE
                echo error both query and node dependent on each other
                return 1
            else
                ##call interpret nodePath  ...see below
                ## call interpret queryPath after
            end
        else

            show $conseilResult

            dispatch $CHECKS[$i] $conseilResult
        end


        #breakpoint



        ##set -gx IQPATH (interpretAST $parsedQueryPath)
        ##echo $IQPATH
        ## breakpoint
        ##do this first: else interpret querypath,(later...interpret query/using scan-object to parse query)
        ##  and then run query 
        ## call interpret node
    end

    unsetConseilLevel

    #breakpoint



    eraseGlobals
end
