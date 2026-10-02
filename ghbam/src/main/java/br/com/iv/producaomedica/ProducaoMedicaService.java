package br.com.iv.producaomedica;
import static java.time.temporal.WeekFields.ISO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

@ApplicationScoped
public class ProducaoMedicaService {
    private final ProducaoMedicaRepository repository;
    @Inject public ProducaoMedicaService(ProducaoMedicaRepository repository){this.repository=repository;}
    public ProducaoMedicaRelatorio gerar(LocalDate inicio,LocalDate fim,String unidade,String matricula){
        validar(inicio,fim); List<ProducaoMedicaLinha> linhas=repository.buscar(inicio,fim,unidade,matricula);
        long tamanho=java.time.temporal.ChronoUnit.DAYS.between(inicio,fim)+1;
        LocalDate fimAnt=inicio.minusDays(1), inicioAnt=fimAnt.minusDays(tamanho-1);
        long anterior=repository.buscar(inicioAnt,fimAnt,unidade,matricula).size(), total=linhas.size();
        long pacientes=linhas.stream().map(ProducaoMedicaLinha::pacienteId).distinct().count();
        long dias=linhas.stream().map(ProducaoMedicaLinha::data).distinct().count();
        long altas=linhas.stream().filter(l->desfecho(l.desfecho())).count();
        return new ProducaoMedicaRelatorio(inicio,fim,norm(unidade),norm(matricula),total,pacientes,dias,
            dias==0?0:round((double)total/dias),altas,total-altas,
            new ProducaoMedicaRelatorio.Comparativo(total,anterior,variacao(total,anterior)),
            agrupar(linhas,l->l.data().toString(),true),agrupar(linhas,ProducaoMedicaService::semana,true),
            agrupar(linhas,l->l.data().format(DateTimeFormatter.ofPattern("yyyy-MM")),true),
            agrupar(linhas,l->Integer.toString(l.data().getYear()),true),porMedico(linhas),
            agrupar(linhas,ProducaoMedicaLinha::unidade,false),agrupar(linhas,ProducaoMedicaLinha::setor,false),
            agrupar(linhas,l->desfecho(l.desfecho())?l.desfecho():"SEM DESFECHO",false),linhas);
    }
    public List<ProducaoMedicaRelatorio.Medico> listarMedicos(LocalDate inicio,LocalDate fim,String unidade){
        validar(inicio,fim);return repository.listarMedicos(inicio,fim,unidade);
    }
    private static void validar(LocalDate i,LocalDate f){if(i==null||f==null)throw new BadRequestException("inicio e fim sao obrigatorios");if(i.isAfter(f))throw new BadRequestException("inicio nao pode ser posterior a fim");if(java.time.temporal.ChronoUnit.DAYS.between(i,f)>1826)throw new BadRequestException("periodo maximo permitido e de 5 anos");}
    private static List<ProducaoMedicaRelatorio.Agrupamento> agrupar(List<ProducaoMedicaLinha> ls,Function<ProducaoMedicaLinha,String> chave,boolean cron){
        Comparator<Map.Entry<String,Long>> c=cron?Map.Entry.comparingByKey():Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey());
        return ls.stream().collect(Collectors.groupingBy(chave,Collectors.counting())).entrySet().stream().sorted(c).map(e->new ProducaoMedicaRelatorio.Agrupamento(e.getKey(),e.getValue())).toList();
    }
    private static List<ProducaoMedicaRelatorio.Medico> porMedico(List<ProducaoMedicaLinha> ls){return ls.stream().collect(Collectors.groupingBy(l->l.matricula()+"\u0000"+l.medico(),Collectors.counting())).entrySet().stream().map(e->{String[] p=e.getKey().split("\u0000",-1);return new ProducaoMedicaRelatorio.Medico(p[0],p[1],"","",false,e.getValue());}).sorted(Comparator.comparingLong(ProducaoMedicaRelatorio.Medico::total).reversed().thenComparing(ProducaoMedicaRelatorio.Medico::nome)).toList();}
    private static String semana(ProducaoMedicaLinha l){return "%04d-S%02d".formatted(l.data().get(ISO.weekBasedYear()),l.data().get(ISO.weekOfWeekBasedYear()));}
    private static boolean desfecho(String d){return d!=null&&!d.isBlank()&&!"-".equals(d.trim());}
    private static double variacao(long a,long p){if(p==0)return a==0?0:100;return round(((double)a-p)*100/p);}
    private static double round(double v){return BigDecimal.valueOf(v).setScale(2,RoundingMode.HALF_UP).doubleValue();}
    private static String norm(String v){return v==null||v.isBlank()?null:v.trim();}
}
