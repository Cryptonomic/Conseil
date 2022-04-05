source fetch.fish
source utils.fish
source system.fish




function subTokenWithValue
    set token (echo $argv[1])
    set subId (echo $argv[1] | jq '.sub' | remove-double-qoutes)
    set type (echo $argv[1] | jq '.typeToken' | remove-double-qoutes)
    set withValue (echo $argv[2] | remove-double-qoutes)
    set state (getTokenState $token)
    #echo "sub: $substitute , withValue: $withValue , stateBefore: $state"
    set newState (subState $state $subId $withValue)
    setTokenState $token $newState


end
function subState
    set state $argv[1]
    set subId $argv[2]
    set withValue $argv[3]

    echo $state | sed -E "s/$subId/$withValue/"
end
function getTokenState
    echo $argv | jq '.state'
end
function setTokenState
    set token (echo $argv[1])
    set state (echo $argv[2])
    echo $token | jq -c ".state = $state"
end




function interpretAST
    #echo $argv | jq -C '.'
    set AST $argv
    for i in (stepLengthReverse $AST)

        set thisNode (echo $AST | jqIdx $i)
        if test $i -eq (length $AST)
            set lastState (getTokenState $thisNode)
        end

        #breakpoint
        set lastState (interpret-token (setTokenState $thisNode $lastState))
        #breakpoint
    end


    echo $lastState
    #new-line


end
function interpretASTtry
    #echo $argv | jq -C '.'
    set AST $argv
    for i in (stepLeng $AST)

        set thisNode (echo $AST | jqIdx $i)
        if test $i -le (length $AST)
            set nextNode   ( echo $AST | jqIdx (math $i + 1 ) )
	    else
            set nextNode   ( echo $AST | jqIdx (math $i + 1 ) )
        end
              
             set  nextState (getTokenState $nextNode)
        #breakpoint
        set nextState (interpret-token (setTokenState $thisNode $nextState))
        #breakpoint
    end


    echo $nextState
    #new-line


end
function interpret-token

    set token $argv
    set type (echo $argv | jq '.typeToken' | remove-double-qoutes)
    set value (echo $argv | jq '.valueToken' | remove-double-qoutes)


    function code
        set codeToken $argv
        set childTokens (echo $argv | jq -c '.interpret')
        set length (length $childTokens)
        for i in (stepLengthReverse $childTokens)
            set thisToken (echo $childTokens | jqIdx $i)


            if test $i -eq $length
                set lastState (getTokenState $thisToken)
            else

                set thisToken (setTokenState $thisToken $lastState)
            end
            #breakpoint

            # echo $thisToken | jq '.'

            # echo "i: $i "
            # echo "lastState: $lastState "
            #breakpoint 
            # set thisToken (setTokenState $thisToken $lastState)
            #set tokenValue (interpret-token $thisToken)
            #set thisToken (subTokenWithValue $thisToken $tokenValue)
            #set lastState (getTokenState $thisToken)
            #set thisToken (setTokenState $thisToken $lastState) 

            set thisToken (setTokenState $thisToken $lastState)

            set tokenType (echo $thisToken| jq '.typeToken' | remove-double-qoutes)
            switch $tokenType
                case Math

                    set lastState (interpret-token $thisToken)

                case '*'
                    set lastState (getTokenState (subTokenWithValue $thisToken (interpret-token $thisToken)))
            end



            #echo "this state: $lastState "
            #breakpoint

            # ##breakpoint
        end
        set codeToken (subTokenWithValue $codeToken $lastState)
        set state (getTokenState $codeToken)
        echo $state
        #breakpoint

    end


    function S
        function conseilHeadLevel
            get -p (block-head) | jq '.level' | remove-double-qoutes
        end

        switch $argv
            case clevel
                getConseilLevel
            case network
                echo $CNETWORK
            case '*'
                echo unknown
        end
        #	breakpoint
    end

    function Q
        switch $argv
            case out
                echo $selectedQueryObject
            case '*'
                echo $selectedQueryObject | jq -c ".$argv"
        end
        #	breakpoint
    end
    function N
        switch $argv
            case out
                echo $nodeResult
            case '*'
                echo $nodeResult | jq -c ".$argv"
        end
    end
    function Math
        function commaToSemiColon
            while read -l out
                echo $out | sed -E "s/,/;/g"
            end
        end
        function mathVariablesState
            echo $argv | string split ';' | grep '=' | sed -E 's/(.*)/\1;/' | string join ''
        end
        set token $argv[1]
        set value $argv[2]
        switch $value
            case '.'
                set bcCode (getTokenState (subTokenWithValue $token $varState) | commaToSemiColon | remove-double-qoutes)
                set -gx varState (mathVariablesState $bcCode)
            case '*'
                set scale (echo $value | sed -E "s/([0-9]+).*/\1/")
                set scale "scale=$scale;"
                set bcCode (getTokenState (subTokenWithValue $token $scale) | commaToSemiColon | remove-double-qoutes)
                set -gx varState (mathVariablesState $bcCode)

                #breakpoint	
        end

        echo $bcCode | bc -l
        #breakpoint
    end

    switch $type
        case code
            code $token
        case S
            S $value
        case Q
            Q $value
        case N
            N $value
        case Math
            Math $token $value
        case UNIT
            echo $value
    end
    ##	breakpoint
end
