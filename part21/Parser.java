import java.util.ArrayList;
public class Parser {

    private Symtab symtab = new Symtab();
    //public static string tempString;

    static int count = 0;
    static int level = 0;
    static int loopID = 0;
    ArrayList<Integer> loopList = new ArrayList<Integer>();
    ArrayList<Integer> excntList = new ArrayList<Integer>();
    ArrayList<Integer> excntLineNum = new ArrayList<Integer>();
    static int excntNum = 0;
    //ArrayList<Dump> valueList = new ArrayList<Dump>();

    // the first sets.
    // note: we cheat sometimes:
    // when there is only a single token in the set,
    // we generally just compare tkrep with the first token.
    TK f_declarations[] = {TK.VAR, TK.none};
    TK f_statement[] = {TK.ID, TK.PRINT, TK.SKIP, TK.STOP, TK.IF, TK.DO, TK.FA, TK.BREAK, TK.DUMP, TK.EXCNT, TK.none};
    TK f_print[] = {TK.PRINT, TK.none};
    TK f_skip[] = {TK.SKIP, TK.none};
    TK f_break[] = {TK.BREAK, TK.none};
    TK f_dump[] = {TK.DUMP, TK.none};
    TK f_excnt[] = {TK.EXCNT, TK.none};
    TK f_stop[] = {TK.STOP, TK.none};
    TK f_assignment[] = {TK.ID, TK.none};
    TK f_if[] = {TK.IF, TK.none};
    TK f_do[] = {TK.DO, TK.none};
    TK f_fa[] = {TK.FA, TK.none};
    TK f_expression[] = {TK.ID, TK.NUM, TK.LPAREN, TK.none};

    // tok is global to all these parsing methods;
    // scan just calls the scanner's scan method and saves the result in tok.
    private Token tok; // the current token
    private void scan() {
        tok = scanner.scan();
    }


    private Scan scanner;
    Parser(Scan scanner) {
        this.scanner = scanner;
        scan();
        program();
        if( tok.kind != TK.EOF )
            parse_error("junk after logical end of program");
        symtab.reportVariables();
    }

    //private void gcsave(String str) {
    //    tempString = tempString + str;
    //}

    // print something in the generated code
    private void gcprint(String str) {
        System.out.println(str);
    }
    // print identifier in the generated code
    // it prefixes x_ in case id conflicts with C keyword.
    private void gcprintid(String str) {
        System.out.println("x_"+str);
    }

    private void program() {
        // generate the E math functions:
        gcprint("#include <stdlib.h>");
        gcprint("int esquare(int x){ return x*x;}");
        gcprint("#include <math.h>");
        gcprint("int esqrt(int x){ double y; if (x < 0) return 0; y = sqrt((double)x); return (int)y;}");
        gcprint("#define MAX(a,b) (((a)>(b))?(a):(b))");
        gcprint("#include <stdio.h>");
    modFunc(); 
        gcprint("int main() {");
        gcprint("int exNum = 0;");
        gcprint("int arrExcnt[100];");
        
        gcprint("for(int i = 0; i < 100; i++) { arrExcnt[i] = (-1);}");
	block();
        if (excntList.size() > 0) {
            gcprint("label_excnt:");
            gcprint("printf(\"---- Execution Counts ----\\n\");");
            gcprint("printf(\"%4s %4s %8s\\n\", \"num\", \"line\", \"count\");");
            for(int i = 0; i < excntList.size(); i++) {
                gcprint("printf(\"%4d %4d %8d\\n\", " + excntList.get(i) + "," + excntLineNum.get(i) + " ,(arrExcnt[" + i + "] + 1));");
            }
        }

        gcprint("return 0; }");
    }

    private void modFunc() {    //; else if(a < 0 && b < 0) return -1*((-1*a)%(-1*b))
        gcprint("int myMod(int a, int b) {if(b==0){printf(\"\\nmod(a,b) with b=0\\n\"); exit(1);}  else if(a==0) return 0; else if(a%b==0) return 0; else if((a > 0 && b < 0) && (a%(-1*b) != 0)) return ((a%(-1*b))+b); else if((a<0 && b > 0) && ((-1*a)%b != 0)) return b-((-1*a)%b); else return (a%b); }");
    }

    private void block() {
        symtab.begin_st_block();
	gcprint("{");
        if( first(f_declarations) ) {
            declarations();
        }
        statement_list();
        symtab.end_st_block();
	gcprint("}");
    }

    private void declarations() {
        mustbe(TK.VAR);
        while( is(TK.ID) ) {
            if( symtab.add_var_entry(tok.string,tok.lineNumber) ) {
                gcprint("int");
                gcprintid(tok.string);
                gcprint("= -12345;");
            }
            scan();
        }
        mustbe(TK.RAV);
    }

    private void statement_list(){
        while( first(f_statement) ) {

            statement();
        }
    }

    private void statement(){
        if( first(f_assignment) )
            assignment();
        else if( first(f_print) )
            print();
        else if( first(f_skip) )
            skip();
        else if( first(f_stop) )
            stop();
        else if( first(f_if) )
            ifproc();
        else if( first(f_do) ){
            loopID++;
            loopList.add(loopID);
            /*for(int i = 0; i < loopList.size(); i++)
                System.out.println(loopList.get(i));*/
            doproc();
        }
            
        else if( first(f_fa) ) {
            loopID++;
            loopList.add(loopID);
            /*for(int i = 0; i < loopList.size(); i++)
                System.out.println(loopList.get(i));*/
            fa();
        }
            
        else if( first(f_break) )
            breaker();
        else if( first(f_dump) )
            dump();
        else if( first(f_excnt) ) {
            excnt();
        }
        else
            parse_error("statement");
    }

    private void assignment(){
        String id = tok.string;
        int lno = tok.lineNumber; // save it too before mustbe!
        mustbe(TK.ID);
        referenced_id(id, true, lno);
        gcprintid(id);
        mustbe(TK.ASSIGN);
        gcprint("=");
        expression();
        gcprint(";");
    }

    private void print(){
        mustbe(TK.PRINT);
        gcprint("printf(\"%d\\n\", ");
        expression();
        gcprint(");");
    }

    private void skip(){
        mustbe(TK.SKIP);
    }

    private void stop(){
        mustbe(TK.STOP);
        if(excntNum > 0) {
            gcprint("goto label_excnt;");
        }
        //if there are statements withing the same block
        //after the 'stop' write error message. 
        if(first(f_statement)){
            System.err.println("warning: on line " + tok.lineNumber + " statement(s) follows stop statement");
        }
        
        gcprint("exit(0);");
        
    }

    private void ifproc(){
        mustbe(TK.IF);
        guarded_commands(TK.IF);
        mustbe(TK.FI);
    }

    private void doproc(){
        mustbe(TK.DO);
        level++;
        gcprint("while(1){");
        guarded_commands(TK.DO);
        gcprint("}");
        mustbe(TK.OD);
        level--;
        gcprint("label" + loopList.get(loopList.size()-1) + ":" + ";");
        loopList.remove(loopList.size()-1);
    }

    private void fa(){
        mustbe(TK.FA);
        level++;
        gcprint("for(");
        String id = tok.string;
        int lno = tok.lineNumber; // save it too before mustbe!
        mustbe(TK.ID);
        referenced_id(id, true, lno);
        gcprintid(id);
        mustbe(TK.ASSIGN);
        gcprint("=");
        expression();
        gcprint(";");
        mustbe(TK.TO);
        gcprintid(id);
        gcprint("<=");
        expression();
        gcprint(";");
        gcprintid(id);
        gcprint("++)");
        if( is(TK.ST) ) {
            gcprint("if( ");
            scan();
            expression();
            gcprint(")");
        }
        commands();
        mustbe(TK.AF);
        level--;

        gcprint("label" + loopList.get(loopList.size()-1) + ":" + ";");
        loopList.remove(loopList.size()-1);
    }

    private void breaker() {
        int breakNum;
        int goBack;
        int breakLine = tok.lineNumber;
        mustbe(TK.BREAK);
        if(is(TK.NUM)) {
            breakNum = Integer.parseInt(tok.string);
            goBack = (level-breakNum);
            scan();
            if(breakNum == 0)
                System.err.println("warning: on line " + breakLine + " break 0 statement ignored");
            else if(goBack < 0) 
                System.err.println("warning: on line " + breakLine + " break statement exceeding loop nesting ignored");
            else if(goBack >= 0)
            {
                gcprint("goto label" + loopList.get(goBack) + ";");
                if(first(f_statement)){
                    System.err.println("warning: on line " + tok.lineNumber + " statement(s) follows break statement");
                }
            }
            
        }
        else {
            if(level == 0) {
                System.err.println("warning: on line " + breakLine + " break statement outside of loop ignored");
            }
            else if(first(f_statement) && level > 0){
                System.err.println("warning: on line " + tok.lineNumber + " statement(s) follows break statement");
                gcprint("break;");
            }
            else
                gcprint("break;");
        }
        
    }

    private void dump() {
        
        //string x;
        int dumpNum;
        int tempLineNum = tok.lineNumber;
        mustbe(TK.DUMP);
        
        int currLineNum = tok.lineNumber;
        if(is(TK.NUM)) {
            ArrayList<Entry> a = symtab.returnList();
            dumpNum = Integer.parseInt(tok.string); //sets token to dumpNum int
            scan();
            if(dumpNum == 0) {
                gcprint("printf(\"+++ dump on line " + currLineNum + " of level 0 begin +++\\n\");");
                
                for(int j = 0; j < a.size(); j++) {
                    if(a.get(j).getLevel() == dumpNum) {
                        gcprint("printf(\"%12d %3d %3d %s\\n\", x_" + a.get(j).getName() + "," + a.get(j).getLine() + "," + a.get(j).getLevel() + "," + "\"" + a.get(j).getName() + "\""  + ");");
                    }
                }
                gcprint("printf(\"--- dump on line " + currLineNum + " of level 0 end ---\\n\");");
            
            }

            else if ( dumpNum > a.size() ){
                    gcprint("printf(\"+++ dump on line " + currLineNum + " of level 0 begin +++\\n\");");
                    
                    if(dumpNum > a.size()) {
                        System.err.println("warning: on line " + currLineNum + " dump statement level (" + dumpNum + ") exceeds block nesting level (" + level + "). using 0");
                    }
                    for(int j = 0; j < a.size(); j++) {
                        //if(a.get(j).getLevel() == dumpNum) {
                            gcprint("printf(\"%12d %3d %3d %s\\n\", x_" + a.get(j).getName() + "," + a.get(j).getLine() + "," + a.get(j).getLevel() + "," + "\"" + a.get(j).getName() + "\""  + ");");
                        //}
                    }
                gcprint("printf(\"--- dump on line " + currLineNum + " of level 0 end ---\\n\");");
            
            }       
            
            else {
                
                gcprint("printf(\"+++ dump on line " + currLineNum + " of level " + dumpNum + " begin +++\\n\");");
                for(int j = 0; j < a.size(); j++) {
                    if(a.get(j).getLevel() == dumpNum) {
                        gcprint("printf(\"%12d %3d %3d %s\\n\", x_" + a.get(j).getName() + "," + a.get(j).getLine() + "," + a.get(j).getLevel() + "," + "\"" + a.get(j).getName() + "\""  + ");");
                    }
                }
                gcprint("printf(\"--- dump on line " + currLineNum + " of level " + dumpNum + " end ---\\n\");");
            }
            
        }
        else {
            
            ArrayList<Entry> a = symtab.returnList();
            gcprint("printf(\"+++ dump on line " + tempLineNum + " of all levels begin +++\\n\");");
            
            for(int i = 0; i < a.size(); i++) {
                //string x = a[i].linenumber + " " + a[i].level + " " + a[i].name;
                gcprint("printf(\"%12d %3d %3d %s\\n\", x_" + a.get(i).getName() + "," + a.get(i).getLine() + "," + a.get(i).getLevel() + "," + "\"" + a.get(i).getName() + "\""  + ");");
            }
            gcprint("printf(\"--- dump on line " + tempLineNum + " of all levels end ---\\n\");");            
        
        }
    }

    private void excnt() {
        int currLineNum = tok.lineNumber;
       
        mustbe(TK.EXCNT);
        if(excntNum <= 100) {
            excntNum++;
            excntList.add(excntNum);
            excntLineNum.add(currLineNum);
            gcprint("arrExcnt[" + (excntNum - 1) + "] = arrExcnt[" + (excntNum - 1) + "] + 1;");
        }
        if(excntNum > 100) {
            System.err.println("can't parse: line " + currLineNum + " too many EXCNT (more than 100)");
            System.exit(1);
        }
        
        
    }

    private void guarded_commands(TK which){
        guarded_command();
        while( is(TK.BOX) ) {
            scan();
            gcprint("else");
            guarded_command();
        }
        if( is(TK.ELSE) ) {
            gcprint("else");
            scan();
            commands();
        }
        else if( which == TK.DO )
            gcprint("else break;");
    }

    private void guarded_command(){
        gcprint("if(");
        expression();
        gcprint(")");
        commands();
    }

    private void commands(){
        mustbe(TK.ARROW);
        gcprint("{");/* note: generate {} to simplify, e.g., st in fa. */
        block();
        gcprint("}");
    }

    private void expression(){
        simple();
        while( is(TK.EQ) || is(TK.LT) || is(TK.GT) ||
               is(TK.NE) || is(TK.LE) || is(TK.GE)) {
            if( is(TK.EQ) ) gcprint("==");
            else if( is(TK.NE) ) gcprint("!=");
            else gcprint(tok.string);
            scan();
            simple();
        }
    }

    private void simple(){
        term();
        while( is(TK.PLUS) || is(TK.MINUS) ) {
            gcprint(tok.string);
            scan();
            term();
        }
    }

    private void term(){
        factor();
        while(  is(TK.TIMES) || is(TK.DIVIDE) || is(TK.MOD) ) {
            gcprint(tok.string);
            scan();
            factor();
        }
    }

    private void factor(){
        if( is(TK.LPAREN) ) {
            gcprint("(");
            scan();
            expression();
            mustbe(TK.RPAREN);
            gcprint(")");
        }
        else if( is(TK.SQUARE) ) {
            gcprint("esquare(");
            scan();
            expression();
            gcprint(")");
        }
        else if( is(TK.SQRT) ) {
            gcprint("esqrt(");
            scan();
            expression();
            gcprint(")");
        }
        else if( is(TK.ID) ) {
            referenced_id(tok.string, false, tok.lineNumber);
            gcprintid(tok.string);
            scan();
        }
        else if( is(TK.NUM) ) {
            gcprint(tok.string);
            scan();
        }
        else if( is(TK.MODULO) ){
            predef();
        }
        else if(is(TK.MAX) ){
            count++;
            if(count > 5) {
                System.err.println("can't parse: line 2 max expressions nested too deeply (> 5 deep)");
                System.exit(1);
            }
            gcprint("MAX");
            scan();
            mustbe(TK.LPAREN);
            gcprint("(");
            expression();
            mustbe(TK.COMMA);
            gcprint(",");
            expression();
            mustbe(TK.RPAREN);
            gcprint(")");
            count--;
        }
        else
            parse_error("factor");
    }

    private void predef() {
        gcprint("myMod");
        scan();
        mustbe(TK.LPAREN);
        gcprint("(");
        expression();
        mustbe(TK.COMMA);
        gcprint(",");
        expression();
        mustbe(TK.RPAREN);
        gcprint(")");

    }


    // be careful: use lno here, not tok.lineNumber
    // (which may have been advanced by now)
    private void referenced_id(String id, boolean assigned, int lno) {
        Entry e = symtab.search(id);
        if( e == null) {
            System.err.println("undeclared variable "+ id + " on line "
                               + lno);
            System.exit(1);
        }
        e.ref(assigned, lno);
    }

    // is current token what we want?
    private boolean is(TK tk) {
        return tk == tok.kind;
    }

    // ensure current token is tk and skip over it.
    private void mustbe(TK tk) {
        if( ! is(tk) ) {
            System.err.println( "mustbe: want " + tk + ", got " +
                                    tok);
            parse_error( "missing token (mustbe)" );
        }
        scan();
    }
    boolean first(TK [] set) {
        int k = 0;
        while(set[k] != TK.none && set[k] != tok.kind) {
            k++;
        }
        return set[k] != TK.none;
    }

    private void parse_error(String msg) {
        System.err.println( "can't parse: line "
                            + tok.lineNumber + " " + msg );
        System.exit(1);
    }
}
