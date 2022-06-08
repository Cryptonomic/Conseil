source scanner.fish
source interpreter.fish
source utils.fish
source fetch.fish

function 1:N
    set numberOfRecords (length $conseilQueryResult)
    set op (echo $currentCheck | jq -c '.operation.op' | remove-double-qoutes)
    set silenceOk (echo $currentCheck | jq -c ".operation.silenceOk" | remove-double-qoutes)
    for i in (stepLength $conseilQueryResult)
        set -gx selectedQueryObject (echo $conseilQueryResult | jqIdx $i)
        set nodePath (interpretAST $parsedNodePath | remove-double-qoutes)


	if [ $silenceOk = "false" ]

        echo $nodePath
	end

        set -gx nodeResult (node $nodePath)
        #breakpoint
        switch $op
            case eq
                eq
        end
    end
    # breakpoint
end
function eq
    set field1 (echo $currentCheck | jq -c ".operation.field_1" | remove-double-qoutes)
    set field2 (echo $currentCheck | jq -c ".operation.field_2" | remove-double-qoutes)
    set error (echo $currentCheck | jq -c ".operation.error" | remove-double-qoutes)
    set ok (echo $currentCheck | jq -c ".operation.ok" | remove-double-qoutes)
    set silenceOk (echo $currentCheck | jq -c ".operation.silenceOk" | remove-double-qoutes)
    set field1Val (interpretAST (scan-line "#$field1##") | remove-double-qoutes)
    set field2Val (interpretAST (scan-line "#$field2##") | remove-double-qoutes)
    set errorVal (interpretAST (scan-line $error) | remove-double-qoutes)
    #breakpoint
    set eq (expr $field1Val = $field2Val)
    if test $eq -eq 1

        set okVal (interpretAST (scan-line $ok) | remove-double-qoutes)

	if [ $silenceOk = "false" ]

	echo "$field1Val == $field2Val ?"
        ok $okVal
	end
	#breakpoint
    else

        set errorVal (interpretAST (scan-line $error) | remove-double-qoutes)

        error $errorVal
    end
    #breakpoint

end
function compare

    set relation (echo $currentCheck| jq -c '.operation.relation' | remove-double-qoutes)
    switch $relation
        case "1:N"
            1:N
    end
    #	breakpoint

end

function relation

    set relation (echo $argv | jq -c '.operation.relation' | remove-double-qoutes)
    switch $relation
        case "1:N"
            1:N $argv[1] $argv[2]
    end
    #	breakpoint

end

function dispatch
    set type (echo $argv[1] | jq -c '.operation.type' | remove-double-qoutes)
    set -gx currentCheck $argv[1]
    set -gx conseilQueryResult $argv[2]
    switch $type
        case compare
            compare
    end
end

