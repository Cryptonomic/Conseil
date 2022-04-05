function remove-double-qoutes
    while read -l out
        echo $out | sed -E 's/"//g'
    end
end

function length
    argparse f/fish -- $argv
    if set -q _flag_fish
        count $argv
    else
        math (echo $argv | jq -c '.|length' ) - 1
    end

end

function stepLength

    argparse f/fish -- $argv

    if set -q _flag_fish
        set length (length -f $argv)
        seq 1 1 $length
    else
        set length (length $argv)
        seq 0 1 $length
    end

end

function stepLengthReverse

    argparse f/fish -- $argv

    if set -q _flag_fish
        set length (length -f $argv)
        seq $length -1 1
    else
        set length (length $argv)
        seq $length -1 0
    end
end


function jqIdx
    jq -c (string join '' ".[$argv[1]]" $argv[2..])
end

function new-line
    echo -e '\n'
end


function isJsonEmpty
    set isEmpty (echo $argv | jq -c "isempty(.)")
    test $isempty = true
end


function listCodeGlobals
    grep 'set -gx' *.fish | sed -E "s/[^']+set -gx[ ]+([^ ]+)(.*)/\1/" \
        | sed -E 's/.*:.*//' | awk NF
end

function unSet
    while read -l global
        eval (string join '' 'set -e ' "$global")
    end
end

function eraseGlobals
    listCodeGlobals | unSet
end


function show
    echo $argv | jq '.'
end




function error
    pastel paint "#ffffff" --on red $argv[1]
end

function highlight
    pastel paint green --on yellow $argv[1]
end

function highlight2
    pastel paint yellow --on grey $argv[1]
end
function ok
    pastel paint white --on green $argv[1]
end
