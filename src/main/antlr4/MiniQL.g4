grammar MiniQL;


statement
    : query EOF
    ;

query
    : SELECT selectList FROM relationList (WHERE whereExpr)?
    ;

selectList
    : expression (COMMA expression)*
    ;

relationList
    : relation (COMMA relation)*
    ;

relation
    : name=IDENTIFIER (AS? alias=IDENTIFIER)?
    ;

whereExpr
    : whereExpr AND whereExpr            # AndExpr
    | whereExpr OR whereExpr             # OrExpr
    | LPAREN whereExpr RPAREN            # ParenWhere
    | predicate                         # PredWhere
    ;

predicate
    : expression op=(EQ|NE|LT|LE|GT|GE) expression   # Comparison
    | expression IN LPAREN query RPAREN              # InSubquery
    | EXISTS LPAREN query RPAREN                     # ExistsSubquery
    ;

expression
    : left=expression op=(ASTERISK|SLASH) right=expression   # MulDiv
    | left=expression op=(PLUS|MINUS) right=expression       # AddSub
    | LPAREN expression RPAREN                               # ParenArith
    | identifier                                            # ColumnRef
    | constant                                             # ConstRef
    ;

identifier
    : table=IDENTIFIER DOT column=IDENTIFIER
    | column=IDENTIFIER
    ;

constant
    : NUMERIC_LITERAL    # NumericConst
    | QUOTED_STRING      # StringConst
    | NULL               # NullConst
    ;

// ---- keywords (must precede IDENTIFIER; both cases accepted) ----
SELECT : 'SELECT' | 'select';
FROM   : 'FROM'   | 'from';
WHERE  : 'WHERE'  | 'where';
AS     : 'AS'     | 'as';
IN     : 'IN'     | 'in';
EXISTS : 'EXISTS' | 'exists';
NULL   : 'NULL'   | 'null';
AND    : 'AND' | 'and' | '&&';
OR     : 'OR'  | 'or'  | '||';

DOT      : '.';
COMMA    : ',';
ASTERISK : '*';
SLASH    : '/';
PLUS     : '+';
MINUS    : '-';
LPAREN   : '(';
RPAREN   : ')';
EQ : '=';
NE : '!=';
LE : '<=';
GE : '>=';
LT : '<';
GT : '>';

QUOTED_STRING
    : '\'' ( ~('\''|'\\') | ('\\' .) )* '\''
    | '"'  ( ~('"' |'\\') | ('\\' .) )* '"'
    ;

NUMERIC_LITERAL
    : INTEGER_VALUE
    | DECIMAL_VALUE
    ;

INTEGER_VALUE : DIGIT+ ;

DECIMAL_VALUE
    : DIGIT+ '.' DIGIT*
    | '.' DIGIT+
    ;

IDENTIFIER : LETTER (LETTER | DIGIT | '_')* ;

fragment DIGIT  : [0-9] ;
fragment LETTER : [a-zA-Z] ;

WS : [ \t\r\n]+ -> skip ;
