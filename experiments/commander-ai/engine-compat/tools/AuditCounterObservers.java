import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;

/** Build-time proof over the JDK's resolved Java AST. No callback is executed.
 * Unknown calls, mutations, annotations and control flow keep native counter events.
 */
public final class AuditCounterObservers {
 enum Value { TRUE,FALSE,UNKNOWN_SAFE,UNKNOWN }
 enum Flow { CONTINUE,RETURN,MAY_RETURN,BREAK,UNSAFE }
 static final String EVENT="mage.game.events.GameEvent",GAME="mage.game.Game",ENUM=EVENT+".EventType";
 static final class Context {
  final Trees trees;final Types types;final Elements elements;final CompilationUnitTree unit;final Map<Tree,TreePath> paths=new IdentityHashMap<>();
  final ExecutableElement method;final VariableElement parameter;final TypeElement owner;final TypeMirror eventType;final Set<String> required=new TreeSet<>();
  Context(Trees trees,Types types,Elements elements,CompilationUnitTree unit,MethodTree tree){this.trees=trees;this.types=types;this.elements=elements;this.unit=unit;
   new TreePathScanner<Void,Void>(){@Override public Void scan(Tree node,Void v){if(node!=null){TreePath parent=getCurrentPath();paths.put(node,parent==null?new TreePath(unit):new TreePath(parent,node));}return super.scan(node,v);}}.scan(unit,null);
   Element found=element(tree);method=found instanceof ExecutableElement?(ExecutableElement)found:null;parameter=method==null||method.getParameters().isEmpty()?null:method.getParameters().get(0);owner=method!=null&&method.getEnclosingElement() instanceof TypeElement?(TypeElement)method.getEnclosingElement():null;TypeElement event=elements.getTypeElement(EVENT);eventType=event==null?null:event.asType();
  }
  Element element(Tree tree){TreePath p=paths.get(tree);return p==null?null:trees.getElement(p);}
  TypeMirror type(Tree tree){TreePath p=paths.get(tree);return p==null?null:trees.getTypeMirror(p);}
  boolean parameter(ExpressionTree x){return parameter!=null&&parameter.equals(element(unwrap(x)));}
  VariableElement ownField(ExpressionTree raw){ExpressionTree x=unwrap(raw);Element el=element(x);if(!(el instanceof VariableElement)||el.getKind()!=ElementKind.FIELD)return null;
   if(x instanceof MemberSelectTree){String receiver=((MemberSelectTree)x).getExpression().toString();if(!receiver.equals("this")&&!receiver.equals("super"))return null;}
   else if(!(x instanceof IdentifierTree))return null;
   Element parent=el.getEnclosingElement();if(owner==null||!(parent instanceof TypeElement)||el.getModifiers().contains(Modifier.STATIC)&&parent.getKind()!=ElementKind.CLASS&&parent.getKind()!=ElementKind.ENUM||!types.isSubtype(types.erasure(owner.asType()),types.erasure(parent.asType())))return null;return (VariableElement)el;
  }
  boolean stableSelector(ExpressionTree x){VariableElement f=ownField(x);if(f==null)return false;if(f.asType().getKind().isPrimitive())return true;
   if(!f.getModifiers().contains(Modifier.FINAL))return false;
   required.add(elements.getBinaryName((TypeElement)f.getEnclosingElement())+"#"+f.getSimpleName());return true;
  }
 }
 static ExpressionTree unwrap(ExpressionTree x){while(x instanceof ParenthesizedTree)x=((ParenthesizedTree)x).getExpression();return x;}
 static Value flip(Value x){return x==Value.TRUE?Value.FALSE:x==Value.FALSE?Value.TRUE:x;}
 static Flow merge(Flow a,Flow b){if(a==b)return a;if(a==Flow.UNSAFE||b==Flow.UNSAFE||a==Flow.BREAK||b==Flow.BREAK)return Flow.UNSAFE;return Flow.MAY_RETURN;}
 static boolean canonical(TypeMirror type,String name){return type!=null&&type.getKind()==TypeKind.DECLARED&&((TypeElement)((DeclaredType)type).asElement()).getQualifiedName().contentEquals(name);}
 static boolean typeCall(ExpressionTree raw,Context c){ExpressionTree x=unwrap(raw);if(!(x instanceof MethodInvocationTree))return false;MethodInvocationTree m=(MethodInvocationTree)x;
  Element el=c.element(m.getMethodSelect());if(!m.getArguments().isEmpty()||!(el instanceof ExecutableElement)||!el.getSimpleName().contentEquals("getType")||!((TypeElement)el.getEnclosingElement()).getQualifiedName().contentEquals(EVENT)||!(m.getMethodSelect() instanceof MemberSelectTree))return false;
  return c.parameter(((MemberSelectTree)m.getMethodSelect()).getExpression());
 }
 static boolean staticReference(ExpressionTree raw,Context c){ExpressionTree x=unwrap(raw);if(x instanceof IdentifierTree)return true;if(!(x instanceof MemberSelectTree))return false;ExpressionTree receiver=((MemberSelectTree)x).getExpression();Element owner=c.element(receiver);return owner instanceof TypeElement||owner instanceof PackageElement||receiver.toString().equals("this")||receiver.toString().equals("super");}
 static String eventConstant(ExpressionTree raw,Context c){if(!staticReference(raw,c))return null;Element el=c.element(unwrap(raw));return el!=null&&el.getKind()==ElementKind.ENUM_CONSTANT&&el.getEnclosingElement() instanceof TypeElement&&((TypeElement)el.getEnclosingElement()).getQualifiedName().contentEquals(ENUM)?el.getSimpleName().toString():null;}
 static Value value(ExpressionTree raw,String event,Context c){ExpressionTree x=unwrap(raw);
  if(x instanceof LiteralTree&&((LiteralTree)x).getValue() instanceof Boolean)return Boolean.TRUE.equals(((LiteralTree)x).getValue())?Value.TRUE:Value.FALSE;
  Element el=c.element(x);if(staticReference(x,c)&&el instanceof VariableElement&&((VariableElement)el).getConstantValue() instanceof Boolean)return Boolean.TRUE.equals(((VariableElement)el).getConstantValue())?Value.TRUE:Value.FALSE;
  VariableElement field=c.ownField(x);if(field!=null&&field.asType().getKind()==TypeKind.BOOLEAN)return Value.UNKNOWN_SAFE;
  if(x instanceof UnaryTree&&x.getKind()==Tree.Kind.LOGICAL_COMPLEMENT)return flip(value(((UnaryTree)x).getExpression(),event,c));
  if(x instanceof BinaryTree){BinaryTree b=(BinaryTree)x;
   if(x.getKind()==Tree.Kind.EQUAL_TO||x.getKind()==Tree.Kind.NOT_EQUAL_TO){String constant=typeCall(b.getLeftOperand(),c)?eventConstant(b.getRightOperand(),c):typeCall(b.getRightOperand(),c)?eventConstant(b.getLeftOperand(),c):null;
    if(constant!=null){Value v=constant.equals(event)?Value.TRUE:Value.FALSE;return x.getKind()==Tree.Kind.EQUAL_TO?v:flip(v);}}
   Value left=value(b.getLeftOperand(),event,c);
   if(x.getKind()==Tree.Kind.CONDITIONAL_AND){if(left==Value.FALSE)return Value.FALSE;if(left==Value.TRUE)return value(b.getRightOperand(),event,c);if(left==Value.UNKNOWN_SAFE){Value right=value(b.getRightOperand(),event,c);return right==Value.FALSE?Value.FALSE:right==Value.UNKNOWN?Value.UNKNOWN:Value.UNKNOWN_SAFE;}}
   if(x.getKind()==Tree.Kind.CONDITIONAL_OR){if(left==Value.TRUE)return Value.TRUE;if(left==Value.FALSE)return value(b.getRightOperand(),event,c);if(left==Value.UNKNOWN_SAFE){Value right=value(b.getRightOperand(),event,c);return right==Value.TRUE?Value.TRUE:right==Value.UNKNOWN?Value.UNKNOWN:Value.UNKNOWN_SAFE;}}
  }
  if(x instanceof ConditionalExpressionTree){ConditionalExpressionTree t=(ConditionalExpressionTree)x;Value v=value(t.getCondition(),event,c);if(v==Value.TRUE||v==Value.FALSE)return value(v==Value.TRUE?t.getTrueExpression():t.getFalseExpression(),event,c);if(v==Value.UNKNOWN_SAFE){Value a=value(t.getTrueExpression(),event,c),b=value(t.getFalseExpression(),event,c);return a==Value.UNKNOWN||b==Value.UNKNOWN?Value.UNKNOWN:a==b?a:Value.UNKNOWN_SAFE;}}
  // CardImpl creates concrete GameEvent instances for individual counters.
  if(x instanceof InstanceOfTree){InstanceOfTree i=(InstanceOfTree)x;TypeMirror type=c.type(i.getType());if(c.parameter(i.getExpression())&&type!=null&&type.getKind()==TypeKind.DECLARED&&c.eventType!=null&&((TypeElement)((DeclaredType)type).asElement()).getQualifiedName().toString().startsWith("mage.game.events."))return c.types.isSubtype(c.types.erasure(c.eventType),c.types.erasure(type))?Value.TRUE:Value.FALSE;}
  return Value.UNKNOWN;
 }
 static Flow switchFrom(SwitchTree sw,int start,boolean watcher,String event,Context c){if(start<0)return Flow.CONTINUE;List<? extends CaseTree> cases=sw.getCases();
  for(int n=start;n<cases.size();n++){CaseTree branch=cases.get(n);if(branch.getCaseKind()!=CaseTree.CaseKind.STATEMENT)return Flow.UNSAFE;Flow pending=Flow.CONTINUE;
   for(StatementTree child:branch.getStatements()){Flow f=statement(child,watcher,event,c);if(f==Flow.UNSAFE)return f;if(f==Flow.BREAK)return pending==Flow.MAY_RETURN?Flow.MAY_RETURN:Flow.CONTINUE;if(f==Flow.RETURN)return f;if(f==Flow.MAY_RETURN)pending=f;}
   if(pending==Flow.MAY_RETURN)return Flow.UNSAFE;
  }return Flow.CONTINUE;
 }
 static Flow statement(StatementTree s,boolean watcher,String event,Context c){
  if(s==null||s instanceof EmptyStatementTree)return Flow.CONTINUE;
  if(s instanceof BlockTree){Flow pending=Flow.CONTINUE;for(StatementTree child:((BlockTree)s).getStatements()){Flow f=statement(child,watcher,event,c);if(f==Flow.UNSAFE)return f;if(f==Flow.RETURN)return f;if(f==Flow.BREAK)return pending==Flow.MAY_RETURN?Flow.UNSAFE:f;if(f==Flow.MAY_RETURN)pending=f;}return pending;}
  if(s instanceof BreakTree)return ((BreakTree)s).getLabel()==null?Flow.BREAK:Flow.UNSAFE;
  if(s instanceof ReturnTree){ExpressionTree x=((ReturnTree)s).getExpression();return watcher?x==null?Flow.RETURN:Flow.UNSAFE:x!=null&&value(x,event,c)==Value.FALSE?Flow.RETURN:Flow.UNSAFE;}
  if(s instanceof IfTree){IfTree i=(IfTree)s;Value v=value(i.getCondition(),event,c);if(v==Value.UNKNOWN)return Flow.UNSAFE;if(v==Value.UNKNOWN_SAFE)return merge(statement(i.getThenStatement(),watcher,event,c),statement(i.getElseStatement(),watcher,event,c));return statement(v==Value.TRUE?i.getThenStatement():i.getElseStatement(),watcher,event,c);}
  if(s instanceof SwitchTree){SwitchTree sw=(SwitchTree)s;List<? extends CaseTree> cases=sw.getCases();
   if(!typeCall(sw.getExpression(),c)){if(!c.stableSelector(sw.getExpression()))return Flow.UNSAFE;Flow joined=Flow.CONTINUE;for(int n=0;n<cases.size();n++)joined=merge(joined,switchFrom(sw,n,watcher,event,c));return joined;}
   int start=-1,fallback=-1;for(int n=0;n<cases.size();n++){CaseTree branch=cases.get(n);if(branch.getExpressions().isEmpty())fallback=n;for(ExpressionTree label:branch.getExpressions())if(event.equals(eventConstant(label,c)))start=n;}
   return switchFrom(sw,start<0?fallback:start,watcher,event,c);
  }return Flow.UNSAFE;
 }
 static boolean annotations(ModifiersTree modifiers){return modifiers.getAnnotations().stream().allMatch(a->Arrays.asList("Override","SuppressWarnings","Deprecated","FunctionalInterface","java.lang.Override","java.lang.SuppressWarnings","java.lang.Deprecated","java.lang.FunctionalInterface").contains(a.getAnnotationType().toString()));}
 static boolean ignores(MethodTree m,Context c){String name=m.getName().toString();boolean watcher=name.equals("watch");
  if(!watcher&&!name.equals("checksEventType")&&!name.equals("checkEventType")||m.getBody()==null||c.method==null||c.method.getParameters().size()!=2||!annotations(m.getModifiers()))return false;
  if(!canonical(c.method.getParameters().get(0).asType(),EVENT)||!canonical(c.method.getParameters().get(1).asType(),GAME))return false;
  if(c.method.getReturnType().getKind()!=(watcher?TypeKind.VOID:TypeKind.BOOLEAN))return false;
  for(String event:new String[]{"ADD_COUNTER","COUNTER_ADDED"}){Flow f=statement(m.getBody(),watcher,event,c);if(f==Flow.UNSAFE||f==Flow.BREAK||!watcher&&f!=Flow.RETURN)return false;}return true;
 }
 static Map<String,Set<String>> parse(JavaCompiler compiler,StandardJavaFileManager manager,Iterable<? extends JavaFileObject> files,String classpath)throws Exception{
  DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,Arrays.asList("-proc:none","--release","8","-implicit:none","-classpath",classpath),null,files);
  List<CompilationUnitTree> units=new ArrayList<>();task.parse().forEach(units::add);task.analyze();
  Set<java.net.URI> failed=new HashSet<>();for(Diagnostic<? extends JavaFileObject> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR){if(d.getSource()==null)throw new IllegalStateException(d.toString());failed.add(d.getSource().toUri());}
  // Unresolved or invalid files never contribute a proof.
  Map<String,Set<String>> safe=new TreeMap<>();Trees trees=Trees.instance(task);
  for(CompilationUnitTree unit:units){if(failed.contains(unit.getSourceFile().toUri()))continue;
   new TreeScanner<Void,Void>(){final Deque<ClassTree> owners=new ArrayDeque<>();
    @Override public Void visitBlock(BlockTree b,Void v){return null;}
    @Override public Void visitClass(ClassTree owner,Void v){owners.addLast(owner);super.visitClass(owner,v);owners.removeLast();return null;}
    @Override public Void visitMethod(MethodTree m,Void v){if(!Arrays.asList("watch","checkEventType","checksEventType").contains(m.getName().toString()))return null;if(!owners.isEmpty()&&owners.stream().allMatch(o->!o.getSimpleName().toString().isEmpty()&&annotations(o.getModifiers()))){Context c=new Context(trees,task.getTypes(),task.getElements(),unit,m);if(ignores(m,c))safe.put(m.getName()+":"+task.getElements().getBinaryName(c.owner),c.required);}return null;}
   }.scan(unit,null);
  }
  if(!failed.isEmpty())System.out.println("COUNTER_OBSERVER_PROOF: unresolved files excluded="+failed.size());return safe;
 }
 static void selfTest(JavaCompiler compiler,StandardJavaFileManager manager,String cp)throws Exception{
  String[] methods={
   "public void watch(GameEvent e,Game g){if(e.getType()==GameEvent.EventType.ZONE_CHANGE){touch();}}",
   "public void watch(GameEvent e,Game g){if(e.getType()!=GameEvent.EventType.ZONE_CHANGE)return;touch();}",
   "public void watch(GameEvent e,Game g){if(e.getType()==GameEvent.EventType.COUNTER_ADDED){touch();}}",
   "public void watch(GameEvent e,Game g){touch();if(e.getType()==GameEvent.EventType.ZONE_CHANGE){touch();}}",
   "public void watch(GameEvent e,Game g){if(unknown()&&e.getType()==GameEvent.EventType.ZONE_CHANGE)touch();}",
   "public void watch(GameEvent e,Game g){if(e.getType()==GameEvent.EventType.ZONE_CHANGE&&unknown())touch();}",
   "public boolean checkEventType(GameEvent e,Game g){return e.getType()==GameEvent.EventType.COUNTERS_ADDED;}",
   "public boolean checksEventType(GameEvent e,Game g){return e.getType()==GameEvent.EventType.ADD_COUNTER;}",
   "public boolean checkEventType(GameEvent e,Game g){return e.getType()==GameEvent.EventType.DRAW_CARD||e.getType()==GameEvent.EventType.ZONE_CHANGE;}",
   "public boolean checkEventType(GameEvent e,Game g){return unknown();}",
   "public void watch(GameEvent e,Game g){switch(e.getType()){case ZONE_CHANGE:touch();break;default:break;}}",
   "public void watch(GameEvent e,Game g){switch(e.getType()){case COUNTER_ADDED:touch();break;default:break;}}",
   "public void hidden(){class Local{public void watch(GameEvent e,Game g){}}}",
   "static{class Local{public void watch(GameEvent e,Game g){}}}public void watch(GameEvent e,Game g){touch();}",
   "boolean condition;public void watch(GameEvent e,Game g){if(condition)return;if(e.getType()==GameEvent.EventType.ZONE_CHANGE)touch();}",
   "boolean active;public void watch(GameEvent e,Game g){if(active&&e.getType()==GameEvent.EventType.ZONE_CHANGE)touch();}",
   "final int mode=1;public boolean checksEventType(GameEvent e,Game g){switch(mode){case 1:return e.getType()==GameEvent.EventType.ZONE_CHANGE;case 2:return false;}return false;}",
   "Object mode;public void watch(GameEvent e,Game g){mode.toString();}",
   "@Unknown public void watch(GameEvent e,Game g){if(e.getType()==GameEvent.EventType.ZONE_CHANGE)touch();}",
   // Resolve enum bindings, rather than trusting a field with a similar name.
   "static class Alias{static final GameEvent.EventType ZONE_CHANGE=GameEvent.EventType.COUNTER_ADDED;}public void watch(GameEvent e,Game g){if(e.getType()==Alias.ZONE_CHANGE)touch();}",
   "Boolean active;public void watch(GameEvent e,Game g){if(active&&e.getType()==GameEvent.EventType.ZONE_CHANGE)touch();}"
  };boolean[] expected={true,true,false,false,false,true,true,false,true,false,true,false,false,false,true,true,true,false,false,false,false};
  for(int n=0;n<methods.length;n++){final String source="package proof;import mage.game.events.GameEvent;import mage.game.Game;@interface Unknown{}class Case"+n+"{private void touch(){}private boolean unknown(){return true;}"+methods[n]+"}";
   JavaFileObject file=new SimpleJavaFileObject(java.net.URI.create("string:///Case"+n+".java"),JavaFileObject.Kind.SOURCE){public CharSequence getCharContent(boolean ignore){return source;}};
   boolean actual=!parse(compiler,manager,Collections.singletonList(file),cp).isEmpty();if(actual!=expected[n])throw new AssertionError("Observer proof case "+n+" expected="+expected[n]);
  }System.out.println("COUNTER_OBSERVER_PROOF: "+methods.length+" resolved-parser cases passed");
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=3)throw new IllegalArgumentException("Usage: AuditCounterObservers XMAGE_SOURCE GENERATED_REGISTRY CLASSPATH_FILE");String cp=Files.readString(Paths.get(args[2])).trim();
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("JDK compiler required for source audit");
  Map<String,Set<String>> safe=new TreeMap<>();List<Path> files=new ArrayList<>();
  try(StandardJavaFileManager manager=compiler.getStandardFileManager(null,null,StandardCharsets.UTF_8)){
   selfTest(compiler,manager,cp);
   for(String folder:new String[]{"Mage/src/main/java","Mage.Sets/src"}){Path root=Paths.get(args[0],folder);if(!Files.isDirectory(root))throw new IllegalStateException("Missing pinned source "+root);
    try(Stream<Path> paths=Files.walk(root)){for(Path p:(Iterable<Path>)paths.filter(x->x.toString().endsWith(".java"))::iterator){String text=Files.readString(p);if(text.contains("checkEventType(")||text.contains("checksEventType(")||text.contains("void watch("))files.add(p);}}
   }
   Collections.sort(files);for(int i=0;i<files.size();i+=75){List<java.io.File> batch=files.subList(i,Math.min(i+75,files.size())).stream().map(Path::toFile).collect(Collectors.toList());safe.putAll(parse(compiler,manager,manager.getJavaFileObjectsFromFiles(batch),cp));}
  }
  StringBuilder out=new StringBuilder("package mage.counters;\nimport java.util.*;\n/** Generated from resolved pinned-source proofs; unknown callbacks keep native events. */\nfinal class CounterObserverRegistry {\nstatic final Set<String> IGNORES;\nstatic final Map<String,String[]> REQUIRED;\nstatic {Set<String> set=new HashSet<String>();Map<String,String[]> required=new HashMap<String,String[]>();\n");
  int chunks=(safe.size()+149)/150;for(int i=0;i<chunks;i++)out.append("add").append(i).append("(set,required);\n");out.append("IGNORES=Collections.unmodifiableSet(set);REQUIRED=Collections.unmodifiableMap(required);}\n");List<String> list=new ArrayList<>(safe.keySet());
  for(int i=0;i<chunks;i++){out.append("private static void add").append(i).append("(Set<String> set,Map<String,String[]> required){\n");for(String key:list.subList(i*150,Math.min((i+1)*150,list.size()))){out.append("set.add(\"").append(key).append("\");\n");if(!safe.get(key).isEmpty()){out.append("required.put(\"").append(key).append("\",new String[]{");for(String field:safe.get(key))out.append("\"").append(field).append("\",");out.append("});\n");}}out.append("}\n");}out.append("}\n");
  Path target=Paths.get(args[1]);Files.createDirectories(target.getParent());Files.writeString(target,out.toString());System.out.println("COUNTER_OBSERVER_PROOF: parsed="+files.size()+" callbacks proven irrelevant="+safe.size());
 }
}
